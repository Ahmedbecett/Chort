const CLOUDFLARE_API_BASE = 'https://api.cloudflare.com/client/v4';

export interface StreamImportResult {
  uid: string;
  hlsUrl?: string;
  dashUrl?: string;
  readyToStream?: boolean;
}

export class CloudflareStreamService {
  static isConfigured(): boolean {
    return Boolean(
      process.env.CLOUDFLARE_STREAM_ACCOUNT_ID &&
      process.env.CLOUDFLARE_STREAM_API_TOKEN
    );
  }

  static async importFromUrl(params: {
    url: string;
    name: string;
    creator?: string;
  }): Promise<StreamImportResult> {
    if (!this.isConfigured()) {
      throw new Error('Cloudflare Stream is not configured');
    }

    const accountId = process.env.CLOUDFLARE_STREAM_ACCOUNT_ID!;
    const token = process.env.CLOUDFLARE_STREAM_API_TOKEN!;

    const response = await fetch(
      `${CLOUDFLARE_API_BASE}/accounts/${accountId}/stream/copy`,
      {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          url: params.url,
          meta: {
            name: params.name,
            creator: params.creator || '',
          },
          creator: params.creator,
        }),
        signal: AbortSignal.timeout(30000),
      }
    );

    const payload = await response.json() as {
      success?: boolean;
      errors?: Array<{ message?: string }>;
      result?: {
        uid?: string;
        readyToStream?: boolean;
        playback?: { hls?: string; dash?: string };
      };
    };

    if (!response.ok || !payload.success || !payload.result?.uid) {
      const reason = payload.errors?.map((e) => e.message).filter(Boolean).join('; ');
      throw new Error(reason || `Cloudflare Stream import failed (HTTP ${response.status})`);
    }

    return {
      uid: payload.result.uid,
      hlsUrl: payload.result.playback?.hls,
      dashUrl: payload.result.playback?.dash,
      readyToStream: payload.result.readyToStream,
    };
  }

  static async deleteVideo(uid: string): Promise<void> {
    if (!this.isConfigured() || !uid) return;

    const accountId = process.env.CLOUDFLARE_STREAM_ACCOUNT_ID!;
    const token = process.env.CLOUDFLARE_STREAM_API_TOKEN!;

    const response = await fetch(
      `${CLOUDFLARE_API_BASE}/accounts/${accountId}/stream/${encodeURIComponent(uid)}`,
      {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${token}` },
        signal: AbortSignal.timeout(15000),
      }
    );

    if (!response.ok && response.status !== 404) {
      throw new Error(`Cloudflare Stream delete failed (HTTP ${response.status})`);
    }
  }
}
