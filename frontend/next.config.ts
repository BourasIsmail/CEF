import type { NextConfig } from "next";

/**
 * Le navigateur ne parle qu'à Next.js : /api/* est relayé vers le backend. Ainsi les cookies httpOnly et le
 * cookie XSRF-TOKEN appartiennent à une seule origine (pas de CORS, pas de souci SameSite).
 * BACKEND_URL est lu au build (voir le Dockerfile, ARG BACKEND_URL).
 */
const backend = process.env.BACKEND_URL ?? "http://localhost:8080";

const config: NextConfig = {
  output: "standalone",
  poweredByHeader: false,
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backend}/api/:path*` }];
  },
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "X-Frame-Options", value: "DENY" },
          { key: "Referrer-Policy", value: "same-origin" },
        ],
      },
    ];
  },
};

export default config;
