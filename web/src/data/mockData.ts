import { UserProfile, VideoItem, AudioTrack } from '../types';

/**
 * Production data policy:
 * No demo users, videos, comments, sounds, counters, or sample media are
 * bundled with the web client. Real content must come from the backend.
 */
export const INITIAL_USER: UserProfile = {
  id: '',
  name: '',
  username: '',
  avatar: '',
  bio: '',
  level: 0,
  followingCount: 0,
  followersCount: 0,
  likesCount: 0,
  isVerified: false,
  statusText: '',
  coins: 0,
  balanceDzd: 0,
};

export const INITIAL_VIDEOS: VideoItem[] = [];
export const AUDIO_TRACKS: AudioTrack[] = [];
export const INITIAL_COMMENTS: Array<{
  id: string;
  videoId: string;
  username: string;
  userAvatar: string;
  text: string;
  likes: number;
  timestamp: string;
}> = [];

export const ALGERIA_WILAYAS = [
  '01 - أدرار (Adrar)',
  '02 - الشلف (Chlef)',
  '03 - الأغواط (Laghouat)',
  '04 - أم البواقي (Oum El Bouaghi)',
  '05 - باتنة (Batna)',
  '06 - بجاية (Béjaïa)',
  '07 - بسكرة (Biskra)',
  '08 - بشار (Béchar)',
  '09 - البليدة (Blida)',
  '10 - البويرة (Bouira)',
  '13 - تلمسان (Tlemcen)',
  '15 - تيزي وزو (Tizi Ouzou)',
  '16 - الجزائر العاصمة (Alger)',
  '19 - سطيف (Sétif)',
  '23 - عنابة (Annaba)',
  '25 - قسنطينة (Constantine)',
  '31 - وهران (Oran)',
  '35 - بومرداس (Boumerdès)',
  '47 - غرداية (Ghardaïa)',
];
