/*
 * CyvoraX Suite - C++ TCP Connect Scanner
 * Fast multi-threaded port scanner with SYN/Connect probe support.
 */
#include <iostream>
#include <vector>
#include <string>
#include <thread>
#include <atomic>
#include <cstring>

#ifdef _WIN32
#include <winsock2.h>
#include <ws2tcpip.h>
#pragma comment(lib, "ws2_32.lib")
typedef int socklen_t;
#else
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <poll.h>
#endif

struct ScanResult {
    int port;
    int status; // 1 = open, 0 = closed, -1 = filtered/timeout
};

static std::atomic<int> open_count{0};
static std::atomic<int> closed_count{0};
static std::atomic<int> filtered_count{0};

#ifdef _WIN32
static int poll_fd(SOCKET fd, int timeout_ms) {
    fd_set read_fds, write_fds;
    FD_ZERO(&read_fds);
    FD_ZERO(&write_fds);
    FD_SET(fd, &read_fds);
    FD_SET(fd, &write_fds);
    timeval tv;
    tv.tv_sec = timeout_ms / 1000;
    tv.tv_usec = (timeout_ms % 1000) * 1000;
    return select(0, &read_fds, &write_fds, NULL, &tv);
}
#else
static int poll_fd(int fd, int timeout_ms) {
    struct pollfd pfd;
    pfd.fd = fd;
    pfd.events = POLLIN | POLLOUT;
    return poll(&pfd, 1, timeout_ms);
}
#endif

static int connect_probe(const char* host, int port, int timeout_ms) {
#ifdef _WIN32
    SOCKET sock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (sock == INVALID_SOCKET) return -1;
#else
    int sock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (sock < 0) return -1;
#endif

    // Set non-blocking
#ifdef _WIN32
    u_long mode = 1;
    ioctlsocket(sock, FIONBIO, &mode);
#else
    int flags = fcntl(sock, F_GETFL, 0);
    fcntl(sock, F_SETFL, flags | O_NONBLOCK);
#endif

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(port);
    inet_pton(AF_INET, host, &addr.sin_addr);

    int ret = connect(sock, (struct sockaddr*)&addr, sizeof(addr));
    int result = -1;

    if (ret == 0) {
        result = 1; // immediately connected
    } else {
#ifdef _WIN32
        int err = WSAGetLastError();
        if (err == WSAEWOULDBLOCK || err == WSAEINPROGRESS) {
#else
        int err = errno;
        if (err == EINPROGRESS) {
#endif
            int poll_result = poll_fd(sock, timeout_ms);
            if (poll_result > 0) {
                int so_error = 0;
                socklen_t len = sizeof(so_error);
                getsockopt(sock, SOL_SOCKET, SO_ERROR, (char*)&so_error, &len);
                result = (so_error == 0) ? 1 : 0;
            } else {
                result = -1; // timeout
            }
        } else {
            result = 0; // connection refused = closed
        }
    }

#ifdef _WIN32
    closesocket(sock);
#else
    close(sock);
#endif

    return result;
}

extern "C" {
#ifdef _WIN32
    __declspec(dllexport)
#endif
    int syn_scan_target(const char* host, int port, int timeout_ms) {
        if (!host || port <= 0 || port > 65535) return -1;
        return connect_probe(host, port, timeout_ms > 0 ? timeout_ms : 3000);
    }

#ifdef _WIN32
    __declspec(dllexport)
#endif
    int scan_ports(const char* host, int start_port, int end_port, int timeout_ms, int max_threads) {
        if (!host || start_port <= 0 || end_port > 65535 || start_port > end_port) return -1;

        open_count = 0;
        closed_count = 0;
        filtered_count = 0;

        int threads = max_threads > 0 ? max_threads : 50;
        if (threads > 200) threads = 200;

        std::vector<std::thread> pool;
        std::atomic<int> current_port{start_port};

        auto worker = [&]() {
            while (true) {
                int p = current_port.fetch_add(1);
                if (p > end_port) break;
                int status = connect_probe(host, p, timeout_ms > 0 ? timeout_ms : 3000);
                if (status == 1) open_count++;
                else if (status == 0) closed_count++;
                else filtered_count++;
            }
        };

        for (int i = 0; i < threads && i <= (end_port - start_port); i++) {
            pool.emplace_back(worker);
        }

        for (auto& t : pool) {
            t.join();
        }

        return open_count.load();
    }

#ifdef _WIN32
    __declspec(dllexport)
#endif
    int get_open_count() { return open_count.load(); }

#ifdef _WIN32
    __declspec(dllexport)
#endif
    int get_closed_count() { return closed_count.load(); }

#ifdef _WIN32
    __declspec(dllexport)
#endif
    int get_filtered_count() { return filtered_count.load(); }
}
