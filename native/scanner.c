/*
 * CyvoraX Suite - native fast port scanner
 * Multi-threaded TCP connect scanner exposed as a shared lib for Python ctypes.
 * Not a raw-socket SYN scanner (that needs root/CAP_NET_RAW), this is a
 * portable, no-privilege-required connect() scanner, but written in C for
 * real thread-level speed vs a Python socket loop.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/select.h>

#define MAX_THREADS 200

typedef struct {
    const char *host;
    int *ports;
    int nports;
    int start_idx;
    int stride;
    int timeout_ms;
    int *results;      /* results[i] = 1 if open, 0 otherwise, indexed same as ports */
} scan_args_t;

static int connect_with_timeout(const char *host, int port, int timeout_ms) {
    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) return 0;

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons((uint16_t)port);
    if (inet_pton(AF_INET, host, &addr.sin_addr) <= 0) {
        close(sock);
        return 0;
    }

    int flags = fcntl(sock, F_GETFL, 0);
    fcntl(sock, F_SETFL, flags | O_NONBLOCK);

    int rc = connect(sock, (struct sockaddr *)&addr, sizeof(addr));
    if (rc < 0 && errno != EINPROGRESS) {
        close(sock);
        return 0;
    }

    fd_set wfds;
    FD_ZERO(&wfds);
    FD_SET(sock, &wfds);
    struct timeval tv;
    tv.tv_sec = timeout_ms / 1000;
    tv.tv_usec = (timeout_ms % 1000) * 1000;

    rc = select(sock + 1, NULL, &wfds, NULL, &tv);
    int is_open = 0;
    if (rc > 0) {
        int so_error = 0;
        socklen_t len = sizeof(so_error);
        getsockopt(sock, SOL_SOCKET, SO_ERROR, &so_error, &len);
        if (so_error == 0) is_open = 1;
    }
    close(sock);
    return is_open;
}

static void *worker(void *arg) {
    scan_args_t *a = (scan_args_t *)arg;
    for (int i = a->start_idx; i < a->nports; i += a->stride) {
        a->results[i] = connect_with_timeout(a->host, a->ports[i], a->timeout_ms);
    }
    return NULL;
}

/*
 * scan_ports: entry point called from Python via ctypes.
 * host: dotted IPv4 string (resolve DNS on the Python side first)
 * ports: array of port numbers to check
 * nports: length of ports array
 * timeout_ms: per-connection timeout
 * results: caller-allocated int array of length nports, filled with 0/1
 * nthreads: number of worker threads to use (capped at MAX_THREADS)
 */
__attribute__((visibility("default")))
void scan_ports(const char *host, int *ports, int nports, int timeout_ms, int *results, int nthreads) {
    if (nthreads < 1) nthreads = 1;
    if (nthreads > MAX_THREADS) nthreads = MAX_THREADS;
    if (nthreads > nports) nthreads = nports;

    pthread_t threads[MAX_THREADS];
    scan_args_t args[MAX_THREADS];

    for (int t = 0; t < nthreads; t++) {
        args[t].host = host;
        args[t].ports = ports;
        args[t].nports = nports;
        args[t].start_idx = t;
        args[t].stride = nthreads;
        args[t].timeout_ms = timeout_ms;
        args[t].results = results;
        pthread_create(&threads[t], NULL, worker, &args[t]);
    }
    for (int t = 0; t < nthreads; t++) {
        pthread_join(threads[t], NULL);
    }
}
