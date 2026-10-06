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
  } = {}) {
    if (this.selectedProvider() === 'coverr') return CoverrService.getVideos(options);
    return PexelsService.getVideos(options);
  }
}
