import { CoverrService } from './coverr.service';
import { PexelsService } from './pexels.service';

export type ExternalProviderName = 'pexels' | 'coverr';

/**
 * Selects the active licensed-video provider. VIDEO_PROVIDER=coverr activates
 * Coverr; anything else (or unset) keeps the historic Pexels behavior, so
 * existing deployments are unchanged until the switch is explicitly enabled.
 * PEXELS_API_KEY must stay configured as an instant rollback path.
 */
export class ExternalVideoService {
  public static selectedProvider(): ExternalProviderName {
    const requested = (process.env.VIDEO_PROVIDER || 'pexels').trim().toLowerCase();
    return requested === 'coverr' ? 'coverr' : 'pexels';
  }

  public static isConfigured(): boolean {
    if (this.selectedProvider() === 'coverr') return CoverrService.isConfigured();
    return PexelsService.isConfigured();
  }

  public static async getVideos(options: {
    query?: string;
    page?: number;
    perPage?: number;
    verifyAudio?: boolean;
  } = {}) {
    return this.getVideosFrom(this.selectedProvider(), options);
  }

  public static async getVideosFrom(
    provider: ExternalProviderName,
    options: { query?: string; page?: number; perPage?: number; verifyAudio?: boolean } = {}
  ) {
    if (provider === 'coverr') return CoverrService.getVideos(options);
    return PexelsService.getVideos(options);
  }

  /** The configured standby: when the selected catalog yields nothing usable,
   *  the Feed Engine backfills from the other licensed source instead of
   *  serving a thin page. Returns null when the other key is absent. */
  public static fallbackProvider(): ExternalProviderName | null {
    const other: ExternalProviderName = this.selectedProvider() === 'coverr' ? 'pexels' : 'coverr';
    return this.isProviderConfigured(other) ? other : null;
  }

  public static isProviderConfigured(provider: ExternalProviderName): boolean {
    if (provider === 'coverr') return CoverrService.isConfigured();
    return PexelsService.isConfigured();
  }
}
