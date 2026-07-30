// CyvoraX Suite - Interactive Website Logic & Animations

document.addEventListener('DOMContentLoaded', () => {
  initParticleCanvas();
  initCounters();
  initSimulator();
  initPolyglotTabs();
});

// Particle Canvas Background
function initParticleCanvas() {
  const canvas = document.getElementById('particleCanvas');
  if (!canvas) return;

  const ctx = canvas.getContext('2d');
  let width = (canvas.width = window.innerWidth);
  let height = (canvas.height = window.innerHeight);

  window.addEventListener('resize', () => {
    width = canvas.width = window.innerWidth;
    height = canvas.height = window.innerHeight;
  });

  const particles = [];
  const particleCount = Math.min(Math.floor(width / 20), 70);

  for (let i = 0; i < particleCount; i++) {
    particles.push({
      x: Math.random() * width,
      y: Math.random() * height,
      vx: (Math.random() - 0.5) * 0.4,
      vy: (Math.random() - 0.5) * 0.4,
      size: Math.random() * 1.8 + 1,
      alpha: Math.random() * 0.5 + 0.2
    });
  }

  function draw() {
    ctx.clearRect(0, 0, width, height);

    // Draw Particles & Connections
    for (let i = 0; i < particles.length; i++) {
      const p = particles[i];
      p.x += p.vx;
      p.y += p.vy;

      if (p.x < 0) p.x = width;
      if (p.x > width) p.x = 0;
      if (p.y < 0) p.y = height;
      if (p.y > height) p.y = 0;

      ctx.beginPath();
      ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2);
      ctx.fillStyle = `rgba(20, 184, 166, ${p.alpha})`;
      ctx.fill();

      // Connect nearby particles
      for (let j = i + 1; j < particles.length; j++) {
        const p2 = particles[j];
        const dx = p.x - p2.x;
        const dy = p.y - p2.y;
        const dist = Math.sqrt(dx * dx + dy * dy);

        if (dist < 120) {
          ctx.beginPath();
          ctx.moveTo(p.x, p.y);
          ctx.lineTo(p2.x, p2.y);
          ctx.strokeStyle = `rgba(20, 184, 166, ${0.15 * (1 - dist / 120)})`;
          ctx.lineWidth = 0.8;
          ctx.stroke();
        }
      }
    }

    requestAnimationFrame(draw);
  }

  draw();
}

// Counter Animations for Metrics
function initCounters() {
  const counters = document.querySelectorAll('.counter-val');
  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          const target = +entry.target.getAttribute('data-target');
          const duration = 1500; // ms
          const step = Math.ceil(target / (duration / 16));
          let current = 0;

          const timer = setInterval(() => {
            current += step;
            if (current >= target) {
              entry.target.innerText = target.toLocaleString();
              clearInterval(timer);
            } else {
              entry.target.innerText = current.toLocaleString();
            }
          }, 16);

          observer.unobserve(entry.target);
        }
      });
    },
    { threshold: 0.5 }
  );

  counters.forEach((counter) => observer.observe(counter));
}

// Live Interactive HTTP Request / Intruder Simulator
function initSimulator() {
  const methodSelect = document.getElementById('simMethod');
  const urlInput = document.getElementById('simUrl');
  const sendBtn = document.getElementById('simSendBtn');
  const rawReqArea = document.getElementById('simRawReq');
  const respArea = document.getElementById('simRespText');
  const statusCode = document.getElementById('simStatusCode');
  const respTime = document.getElementById('simRespTime');

  if (!sendBtn) return;

  const mockResponses = {
    'GET /api/v1/auth/session': {
      status: '200 OK',
      time: '12ms',
      color: 'text-emerald-400',
      body: `HTTP/1.1 200 OK\r\nDate: Thu, 30 Jul 2026 18:30:00 GMT\r\nContent-Type: application/json\r\nServer: CyvoraX Netty Core 1.6.1\r\nX-CyvoraX-Intercept: Pass-Through\r\n\r\n{\n  "status": "authenticated",\n  "user_id": "usr_998241",\n  "role": "security_auditor",\n  "permissions": ["PROXY_READ", "INTERCEPT_WRITE", "SCANNER_EXEC"]\n}`
    },
    'POST /api/v1/intruder/attack': {
      status: '200 OK',
      time: '4ms',
      color: 'text-emerald-400',
      body: `HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nX-Intruder-Engine: Turbo-Pipeline v1.6.1\r\n\r\n{\n  "attack_id": "atk_77192",\n  "payloads_sent": 10000,\n  "anomalies_detected": 3,\n  "vulnerabilities": [\n    "IDOR on /user/account",\n    "SQLi in 'sort_by' parameter"\n  ]\n}`
    }
  };

  sendBtn.addEventListener('click', () => {
    sendBtn.disabled = true;
    sendBtn.innerText = '⚡ Intercepting...';
    respArea.innerText = 'Connecting to local Netty 4.1 proxy listener...';

    setTimeout(() => {
      const method = methodSelect.value;
      const url = urlInput.value;
      const key = `${method} ${url}`;

      const res = mockResponses[key] || {
        status: '200 OK',
        time: '8ms',
        color: 'text-emerald-400',
        body: `HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nServer: CyvoraX-Netty-Engine/1.6.1\r\nX-Security-Audit: Passed\r\n\r\n{\n  "message": "CyvoraX Proxy Intercepted Transaction Successfully",\n  "target": "${url}",\n  "timestamp": "${new Date().toISOString()}"\n}`
      };

      statusCode.innerText = res.status;
      statusCode.className = `text-xs px-2 py-0.5 rounded font-mono font-bold ${res.color} bg-emerald-950/60 border border-emerald-800`;
      respTime.innerText = res.time;
      respArea.innerText = res.body;

      sendBtn.disabled = false;
      sendBtn.innerText = '🚀 Send Request';
    }, 400);
  });
}

// Polyglot Engine Tab Switcher
function initPolyglotTabs() {
  const tabs = document.querySelectorAll('.polyglot-tab');
  const codeDisplay = document.getElementById('polyglotCode');

  if (!codeDisplay) return;

  const polyglotSnippets = {
    c: `// C Language Native Engine (src/main/native/c_engine.c)
#include <stdio.h>
#include <sys/socket.h>
#include <netinet/in.h>

void cyvorax_raw_socket_scan(const char* host, int port) {
    printf("[CyvoraX-C] Performing zero-copy raw TCP handshake scan on %s:%d\\n", host, port);
    // Direct kernel ring buffer packet capture integration
}`,
    cpp: `// C++ Security Scanner (src/main/native/cpp_scanner.cpp)
#include <iostream>
#include <vector>

class CyvoraxCppScanner {
public:
    void execute_parallel_fuzz(const std::vector<std::string>& payloads) {
        std::cout << "[CyvoraX-C++] Executing SIMD-accelerated payload matching\\n";
    }
};`,
    go: `// Go Native Tool Binding (src/main/native/go_bridge.go)
package main

import (
	"fmt"
	"github.com/projectdiscovery/katana/pkg/engine"
)

//export RunKatanaSpider
func RunKatanaSpider(target string) {
	fmt.Printf("[CyvoraX-Go] Launching Katana Crawler Engine for %s\\n", target)
}`,
    rust: `// Rust Memory-Safe Parser (src/main/native/rust_parser/src/lib.rs)
#[no_mangle]
pub extern "C" fn parse_http2_frame_safe(buffer: *const u8, len: usize) -> i32 {
    println!("[CyvoraX-Rust] Zero-allocation HTTP/2 frame validation complete");
    0
}`,
    csharp: `// C# .NET Windows Auth Bridge (src/main/native/csharp_auth.cs)
using System;
using System.Security.Principal;

public class CyvoraxWindowsAuth {
    public static void AuthenticateNTLM(string domain, string user) {
        Console.WriteLine($"[CyvoraX-C#] Authenticating NTLMv2 Session for {domain}\\\\{user}");
    }
}`
  };

  tabs.forEach((tab) => {
    tab.addEventListener('click', () => {
      tabs.forEach((t) => t.classList.remove('bg-teal-500/20', 'text-teal-300', 'border-teal-500'));
      tabs.forEach((t) => t.classList.add('text-gray-400', 'border-transparent'));

      tab.classList.remove('text-gray-400', 'border-transparent');
      tab.classList.add('bg-teal-500/20', 'text-teal-300', 'border-teal-500');

      const lang = tab.getAttribute('data-lang');
      codeDisplay.innerText = polyglotSnippets[lang] || '// Native Engine';
    });
  });
}

// Copy Code Snippet Helper
function copyCode(elementId) {
  const el = document.getElementById(elementId);
  if (!el) return;
  const text = el.innerText || el.textContent;
  navigator.clipboard.writeText(text).then(() => {
    showToast('Copied to clipboard!');
  });
}

// Floating Toast Notification
function showToast(msg) {
  const toast = document.createElement('div');
  toast.className = 'fixed bottom-6 right-6 z-50 bg-teal-900 border border-teal-500 text-teal-200 px-4 py-2.5 rounded-lg shadow-xl text-sm font-semibold flex items-center gap-2 animate-bounce';
  toast.innerHTML = `<span>⚡</span> ${msg}`;
  document.body.appendChild(toast);
  setTimeout(() => toast.remove(), 2500);
}
