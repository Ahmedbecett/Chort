export interface UserProfile {
  id: string;
  name: string;
  username: string;
  avatar: string;
  bio: string;
  level: number;
  followingCount: number;
  followersCount: number;
  likesCount: number;
  isVerified?: boolean;
  statusText?: string;
  coins: number;
  balanceDzd: number;
}

export interface VideoItem {
  id: string;
  url: string;
  thumbnail: string;
  caption: string;
  author: {
    id: string;
    name: string;
    username: string;
    avatar: string;
    isVerified?: boolean;
  };
  song: {
    title: string;
    artist: string;
  };
  likes: number;
  commentsCount: number;
  shares: number;
  saves: number;
  views: number;
  tags: string[];
  isLiked?: boolean;
  isSaved?: boolean;
  isPinned?: boolean;
  commentsDisabled?: boolean;
  isOfflineAvailable?: boolean;
  createdAt: string;
  category?: 'music' | 'trending' | 'dz' | 'humor' | 'gaming' | 'nature';
}

export interface CommentItem {
  id: string;
  videoId: string;
  username: string;
  userAvatar: string;
  text: string;
  likes: number;
  timestamp: string;
}

export interface AudioTrack {
  id: string;
  title: string;
  artist: string;
  duration: string;
  url: string;
  cover: string;
  usesCount: string;
}

export interface ActivityHistoryItem {
  id: string;
  videoId: string;
  thumbnail: string;
  title: string;
  author: string;
  watchedAt: string;
  duration: string;
}

export interface TransactionItem {
  id: string;
  type: 'recharge' | 'withdraw' | 'gift';
  amount: number;
  coins: number;
  status: 'completed' | 'pending';
  method: string;
  date: string;
}

export interface PromoteCampaign {
  id: string;
  videoId: string;
  goal: 'more_views' | 'more_followers' | 'more_profile_visits';
  targetWilayas: string[];
  budgetDzd: number;
  durationDays: number;
  estimatedReach: number;
  status: 'active' | 'review' | 'completed';
  createdAt: string;
}
