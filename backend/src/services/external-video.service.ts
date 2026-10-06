import { CoverrService } from './coverr.service';
import { PexelsService } from './pexels.service';
import { PixabayService } from './pixabay.service';

export type ExternalProviderName = 'pexels' | 'coverr' | 'pixabay';

/**
 * Selects the active licensed-video provider. VIDEO_PROVIDER=coverr activates
 * Coverr; anything else (or unset) keeps the historic Pexels behavior, so
 * existing deployments are unchanged until the switch is explicitly enabled.
 * PEXELS_API_KEY must stay configured as an instant rollback path.
 */
export class ExternalVideoService {
  public static selectedProvider(): ExternalProviderName {
    const requested = (process.env.VIDEO_PROVIDER || 'pexels').trim().toLowerCase();
    if (requested === 'coverr') return 'coverr';
    if (requested === 'pixabay') return 'pixabay';
    return 'pexels';
  }

  public static isConfigured(): boolean {
    return this.isProviderConfigured('coverr') || this.isProviderConfigured('pixabay') || this.isProviderConfigured('pexels');
  }

  public static async getVideos(options: {
    query?: string;
    page?: number;
    perPage?: number;
    verifyAudio?: boolean;
    debug?: boolean;
  } = {}) {
    return this.getVideosFrom(this.selectedProvider(), options);
  }

  public static async getVideosFrom(
    provider: ExternalProviderName,
    options: { query?: string; page?: number; perPage?: number; verifyAudio?: boolean; debug?: boolean } = {}
  ) {
    if (provider === 'coverr') return CoverrService.getVideos(options);
    if (provider === 'pixabay') return PixabayService.getVideos(options);
    return PexelsService.getVideos(options);
  }

  /** Ordered licensed-seed chain: leads with configured providers so failing/unconfigured ones never stall the feed */
  public static seedProviderChain(): ExternalProviderName[] {
    const selected = this.selectedProvider();
    const all: ExternalProviderName[] = ['coverr', 'pixabay', 'pexels'];
    const chain: ExternalProviderName[] = [];
    if (this.isProviderConfigured(selected)) {
      chain.push(selected);
    }
    for (const p of all) {
      if (p !== selected && this.isProviderConfigured(p)) {
        chain.push(p);
      }
    }
    if (chain.length === 0) {
      chain.push(selected);
    }
    return chain;
  }

  public static isProviderConfigured(provider: ExternalProviderName): boolean {
    if (provider === 'coverr') return CoverrService.isConfigured();
    if (provider === 'pixabay') return PixabayService.isConfigured();
    return PexelsService.isConfigured();
  }
}
