/**
 * Rivo AI Moderation Service (baseline heuristic gate).
 *
 * Production publish gate for user-uploaded videos: content must pass
 * moderation before it can become READY/PUBLIC.
 *
 * Current implementation is a deterministic baseline (input validation +
 * blocked-term text screening on the caption). It is intentionally honest
 * about what it checks — see BLOCKED_TERMS below.
 *
 * To upgrade to ML-based moderation (e.g. OpenAI Moderation API, Google
 * Video Intelligence, AWS Rekognition), implement the provider call inside
 * `moderateVideo` behind an environment flag and keep this baseline as the
 * fail-closed fallback when the provider is unreachable.
 */

export interface ModerateVideoInput {
  videoId: string;
  videoUrl: string;
  caption: string;
  objectKey: string;
}

export interface ModerationVerdict {
  allowed: boolean;
  reason?: string;
  /** Which engine produced the verdict (baseline heuristic or a future ML provider). */
  engine: 'baseline-heuristic';
}

// Minimal blocked-term list for caption screening (spam / abuse / scams).
// Extend via configuration as moderation policy evolves.
const BLOCKED_TERMS = [
  'viagra',
  'casino',
  'crypto giveaway',
  'double your money',
  'free followers hack',
  'password dump',
];

export class AIModerationService {
  static async moderateVideo(input: ModerateVideoInput): Promise<ModerationVerdict> {
    if (!input.videoId || !input.videoUrl || !input.objectKey) {
      return {
        allowed: false,
        reason: 'Video metadata is incomplete for moderation review.',
        engine: 'baseline-heuristic',
      };
    }

    const caption = (input.caption || '').toLowerCase();
    const hit = BLOCKED_TERMS.find((term) => term && caption.includes(term));
    if (hit) {
      return {
        allowed: false,
        reason: 'Video caption violates community policy.',
        engine: 'baseline-heuristic',
      };
    }

    return { allowed: true, engine: 'baseline-heuristic' };
  }
}
