/**
 * CyvoraX Suite - JavaScript / TypeScript Playwright SPA Crawler & DOM Sink Tracer
 * Node.js automation engine for React/Vue/Angular Single Page Applications.
 */

const { chromium } = require('playwright');

async function crawlSPA(targetUrl, maxDepth = 2) {
    console.log(`[CyvoraX JS Crawler] Starting Playwright session for: ${targetUrl}`);
    const browser = await chromium.launch({ headless: true });
    const page = await browser.newPage();

    const discoveredUrls = new Set();

    page.on('request', req => {
        discoveredUrls.add(req.url());
    });

    try {
        await page.goto(targetUrl, { waitUntil: 'networkidle', timeout: 15000 });
        const title = await page.title();
        console.log(`[CyvoraX JS Crawler] Page loaded: "${title}" | Discovered endpoints: ${discoveredUrls.size}`);
    } catch (err) {
        console.error(`[CyvoraX JS Crawler Error] ${err.message}`);
    } finally {
        await browser.close();
    }

    return Array.from(discoveredUrls);
}

if (require.main === module) {
    const target = process.argv[2] || 'http://example.com';
    crawlSPA(target);
}

module.exports = { crawlSPA };
