import {
    IUser,
    IStory,
    IChapter,
    IComment,
    IWallet,
    ITeam,
    IDepositPackage
} from '../types';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

class ApiClient {
    private token: string | null = localStorage.getItem('access_token');

    public setToken(token: string | null) {
        this.token = token;
        if (token) {
            localStorage.setItem('access_token', token);
        } else {
            localStorage.removeItem('access_token');
        }
    }

    private async request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
        const headers: Record<string, string> = {
            'Content-Type': 'application/json',
            ...(options.headers as Record<string, string> || {}),
        };

        if (this.token) {
            headers['Authorization'] = `Bearer ${this.token}`;
        }

        const response = await fetch(`${API_BASE_URL}${endpoint}`, {
            ...options,
            headers,
        });

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.detail || errorData.message || `API Error ${response.status}`);
        }

        if (response.status === 24) return {} as T;
        return response.json();
    }

    // ------------------------------------------------------------------------
    // Auth API
    // ------------------------------------------------------------------------
    public async login(email: string, password: string) {
        const res = await this.request<{ accessToken: string; refreshToken: string; user: IUser }>('/auth/login', {
            method: 'POST',
            body: JSON.stringify({ email, password }),
        });
        this.setToken(res.accessToken);
        return res;
    }

    public async register(email: string, username: string, password: string) {
        const res = await this.request<{ accessToken: string; refreshToken: string; user: IUser }>('/auth/register', {
            method: 'POST',
            body: JSON.stringify({ email, username, password }),
        });
        this.setToken(res.accessToken);
        return res;
    }

    public async logout() {
        this.setToken(null);
    }

    public async getCurrentUser(): Promise<IUser> {
        return this.request<IUser>('/auth/me');
    }

    // ------------------------------------------------------------------------
    // Catalog API (Stories & Chapters)
    // ------------------------------------------------------------------------
    public async getStories(params?: { page?: number; limit?: number; search?: string }): Promise<IStory[]> {
        const query = new URLSearchParams(params as Record<string, string>).toString();
        return this.request<IStory[]>(`/stories${query ? `?${query}` : ''}`);
    }

    public async getStoryBySlug(slug: string): Promise<IStory> {
        return this.request<IStory>(`/stories/${slug}`);
    }

    public async getChaptersByStoryId(storyId: string): Promise<IChapter[]> {
        return this.request<IChapter[]>(`/stories/${storyId}/chapters`);
    }

    public async getChapterBySlug(storySlug: string, chapterSlug: string): Promise<IChapter> {
        return this.request<IChapter>(`/stories/${storySlug}/chapters/${chapterSlug}`);
    }

    // ------------------------------------------------------------------------
    // Monetization API (Wallets & Purchases)
    // ------------------------------------------------------------------------
    public async getMyWallet(): Promise<IWallet> {
        return this.request<IWallet>('/wallets/me');
    }

    public async getDepositPackages(): Promise<IDepositPackage[]> {
        return this.request<IDepositPackage[]>('/monetization/deposit-packages');
    }

    public async unlockChapter(chapterId: string): Promise<{ success: boolean; remainingCoin: number }> {
        return this.request<{ success: boolean; remainingCoin: number }>(`/chapters/${chapterId}/unlock`, {
            method: 'POST',
        });
    }

    // ------------------------------------------------------------------------
    // Teams API
    // ------------------------------------------------------------------------
    public async getMyTeams(): Promise<ITeam[]> {
        return this.request<ITeam[]>('/teams/me');
    }

    // ------------------------------------------------------------------------
    // Community API (Comments)
    // ------------------------------------------------------------------------
    public async getComments(targetType: string, targetId: string): Promise<IComment[]> {
        return this.request<IComment[]>(`/comments?targetType=${targetType}&targetId=${targetId}`);
    }

    public async postComment(targetType: string, targetId: string, content: string, parentId?: string): Promise<IComment> {
        return this.request<IComment>('/comments', {
            method: 'POST',
            body: JSON.stringify({ targetType, targetId, content, parentId }),
        });
    }
}

export const api = new ApiClient();
