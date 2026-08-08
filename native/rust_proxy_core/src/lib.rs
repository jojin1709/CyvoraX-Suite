/*
 * CyvoraX Suite - Rust High-Throughput Memory-Safe Proxy Core Crate
 * PyO3 binding exposing proxy status and connection info to Python.
 */
use pyo3::prelude::*;
use std::sync::atomic::{AtomicU64, Ordering};

static TOTAL_REQUESTS: AtomicU64 = AtomicU64::new(0);
static ACTIVE_CONNECTIONS: AtomicU64 = AtomicU64::new(0);
static BYTES_PROXIED: AtomicU64 = AtomicU64::new(0);

#[pyfunction]
fn rust_proxy_status() -> PyResult<String> {
    Ok(format!(
        "CyvoraX Rust Proxy Engine v1.6.1 | Requests: {} | Active: {} | Bytes: {}",
        TOTAL_REQUESTS.load(Ordering::Relaxed),
        ACTIVE_CONNECTIONS.load(Ordering::Relaxed),
        BYTES_PROXIED.load(Ordering::Relaxed)
    ))
}

#[pyfunction]
fn increment_requests() -> PyResult<u64> {
    Ok(TOTAL_REQUESTS.fetch_add(1, Ordering::Relaxed) + 1)
}

#[pyfunction]
fn increment_connections() -> PyResult<u64> {
    Ok(ACTIVE_CONNECTIONS.fetch_add(1, Ordering::Relaxed) + 1)
}

#[pyfunction]
fn decrement_connections() -> PyResult<u64> {
    let current = ACTIVE_CONNECTIONS.load(Ordering::Relaxed);
    if current > 0 {
        Ok(ACTIVE_CONNECTIONS.fetch_sub(1, Ordering::Relaxed) - 1)
    } else {
        Ok(0)
    }
}

#[pyfunction]
fn add_bytes_proxyed(bytes: u64) -> PyResult<u64> {
    Ok(BYTES_PROXIED.fetch_add(bytes, Ordering::Relaxed) + bytes)
}

#[pyfunction]
fn get_stats() -> PyResult<(u64, u64, u64)> {
    Ok((
        TOTAL_REQUESTS.load(Ordering::Relaxed),
        ACTIVE_CONNECTIONS.load(Ordering::Relaxed),
        BYTES_PROXIED.load(Ordering::Relaxed),
    ))
}

#[pymodule]
fn cyvorax_rust_proxy(m: &Bound<'_, PyModule>) -> PyResult<()> {
    m.add_function(wrap_pyfunction!(rust_proxy_status, m)?)?;
    m.add_function(wrap_pyfunction!(increment_requests, m)?)?;
    m.add_function(wrap_pyfunction!(increment_connections, m)?)?;
    m.add_function(wrap_pyfunction!(decrement_connections, m)?)?;
    m.add_function(wrap_pyfunction!(add_bytes_proxyed, m)?)?;
    m.add_function(wrap_pyfunction!(get_stats, m)?)?;
    Ok(())
}
