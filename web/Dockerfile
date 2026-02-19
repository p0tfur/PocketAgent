FROM oven/bun:1 as base
WORKDIR /app

# Copy root workspace files
COPY package.json bun.lockb* tsconfig.json ./

# Copy workspace package.jsons
COPY packages/shared/package.json ./packages/shared/package.json
COPY server/package.json ./server/package.json
COPY web/package.json ./web/package.json

# Install dependencies
RUN bun install --frozen-lockfile

# Copy source code
COPY packages/shared ./packages/shared
COPY web ./web

# Build
WORKDIR /app/web
RUN bun run build

# Runner
FROM oven/bun:1 as runner
WORKDIR /app

COPY --from=base /app/web/build ./build
COPY --from=base /app/web/package.json ./package.json
COPY --from=base /app/node_modules ./node_modules

EXPOSE 3000
CMD ["bun", "build/index.js"]
