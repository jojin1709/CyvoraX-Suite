/*
 * CyvoraX Suite - native fast port scanner (Windows version)
 * Multi-threaded TCP connect scanner exposed as a shared DLL for Python ctypes.
 * Uses Winsock2 + Windows threads for cross-platform build on Windows.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>

#pragma comment(lib, "ws2_32.lib")

#define MAX_THREADS 200

typedef struct {
    const char *host;
    int *ports;
    int nports;
    int start_idx;
    int stride;
    int timeout_ms;
    int *results;
} scan_args_t;

static int connect_with_timeout(const char *host, int port, int timeout_ms) {
    SOCKET sock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (sock == INVALID_SOCKET) return 0;

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons((u_short)port);
    addr.sin_addr.s_addr = inet_addr(host);
    if (addr.sin_addr.s_addr == INADDR_NONE) {
        closesocket(sock);
        return 0;
    }

    /* Set non-blocking */
    u_long mode = 1;
    ioctlsocket(sock, FIONBIO, &mode);

    connect(sock, (struct sockaddr *)&addr, sizeof(addr));

    fd_set wfds, efds;
    FD_ZERO(&wfds);
    FD_ZERO(&efds);
    FD_SET(sock, &wfds);
    FD_SET(sock, &efds);

    struct timeval tv;
    tv.tv_sec  = timeout_ms / 1000;
    tv.tv_usec = (timeout_ms % 1000) * 1000;

    int rc = select(0, NULL, &wfds, &efds, &tv);
    int is_open = 0;
    if (rc > 0 && FD_ISSET(sock, &wfds) && !FD_ISSET(sock, &efds)) {
        int so_error = 0;
        int optlen = sizeof(so_error);
        getsockopt(sock, SOL_SOCKET, SO_ERROR, (char *)&so_error, &optlen);
        if (so_error == 0) is_open = 1;
    }
    closesocket(sock);
    return is_open;
}

static DWORD WINAPI worker(LPVOID arg) {
    scan_args_t *a = (scan_args_t *)arg;
    for (int i = a->start_idx; i < a->nports; i += a->stride) {
        a->results[i] = connect_with_timeout(a->host, a->ports[i], a->timeout_ms);
    }
    return 0;
}

__declspec(dllexport)
void scan_ports(const char *host, int *ports, int nports, int timeout_ms, int *results, int nthreads) {
    /* Init winsock once per call (idempotent in practice) */
    WSADATA wsa;
    WSAStartup(MAKEWORD(2, 2), &wsa);

    if (nthreads < 1) nthreads = 1;
    if (nthreads > MAX_THREADS) nthreads = MAX_THREADS;
    if (nthreads > nports) nthreads = nports;

    HANDLE threads[MAX_THREADS];
    scan_args_t args[MAX_THREADS];

    for (int t = 0; t < nthreads; t++) {
        args[t].host      = host;
        args[t].ports     = ports;
        args[t].nports    = nports;
        args[t].start_idx = t;
        args[t].stride    = nthreads;
        args[t].timeout_ms = timeout_ms;
        args[t].results   = results;
        threads[t] = CreateThread(NULL, 0, worker, &args[t], 0, NULL);
    }
    WaitForMultipleObjects(nthreads, threads, TRUE, INFINITE);
    for (int t = 0; t < nthreads; t++) CloseHandle(threads[t]);

    WSACleanup();
}
