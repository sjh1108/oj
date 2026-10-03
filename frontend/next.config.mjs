/** @type {import('next').NextConfig} */
const nextConfig = {
  // The hand-entry form was removed; old bookmarks land on the bulk import page.
  async redirects() {
    return [
      {
        source: "/admin/problems/new",
        destination: "/admin/problems/import",
        permanent: false,
      },
    ];
  },
};

export default nextConfig;
