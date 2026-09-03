export type CurrentUser = {
  id: string;
  email: string;
  emailVerified: boolean;
  createdAt: string;
  updatedAt: string;
};

type TokenResponse = {
  accessToken: string;
  tokenType: "Bearer";
  expiresIn: 900;
};

export class AuthError extends Error {
  constructor(public readonly kind: "credentials" | "expired" | "rate-limit" | "unexpected") {
    super(kind);
  }
}

export class AuthClient {
  private accessToken?: string;
  private refreshPromise?: Promise<boolean>;

  constructor(
    private readonly apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080",
    private readonly onSessionExpired: () => void = () => undefined,
  ) {}

  async register(email: string, password: string): Promise<void> {
    const response = await fetch(`${this.apiUrl}/api/v1/auth/register`, {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });
    if (!response.ok) throw await this.error(response);
  }

  async login(email: string, password: string): Promise<CurrentUser> {
    const response = await fetch(`${this.apiUrl}/api/v1/auth/login`, {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });
    if (!response.ok) throw await this.error(response);
    this.remember(await response.json() as TokenResponse);
    return this.currentUser();
  }

  async requestPasswordReset(email: string): Promise<void> {
    const response = await fetch(`${this.apiUrl}/api/v1/auth/password-reset-requests`, {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email }),
    });
    if (!response.ok) throw await this.error(response);
  }

  async resetPassword(token: string, newPassword: string): Promise<void> {
    const response = await fetch(`${this.apiUrl}/api/v1/auth/password-resets`, {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token, newPassword }),
    });
    if (!response.ok) throw await this.error(response);
    this.accessToken = undefined;
  }

  async restore(): Promise<CurrentUser | null> {
    if (!await this.refresh()) return null;
    try {
      return await this.currentUser();
    } catch {
      this.accessToken = undefined;
      return null;
    }
  }

  refresh(): Promise<boolean> {
    if (!this.refreshPromise) {
      this.refreshPromise = this.withBrowserLock(async () => {
        try {
          const response = await fetch(`${this.apiUrl}/api/v1/auth/refresh`, {
            method: "POST",
            credentials: "include",
          });
          if (!response.ok) {
            this.accessToken = undefined;
            return false;
          }
          this.remember(await response.json() as TokenResponse);
          return true;
        } catch {
          this.accessToken = undefined;
          return false;
        }
      }).finally(() => {
        this.refreshPromise = undefined;
      });
    }
    return this.refreshPromise;
  }

  async logout(): Promise<void> {
    const token = this.accessToken;
    try {
      if (token) {
        await fetch(`${this.apiUrl}/api/v1/auth/logout`, {
          method: "POST",
          credentials: "include",
          headers: { Authorization: `Bearer ${token}` },
        });
      }
    } finally {
      this.accessToken = undefined;
    }
  }

  async request(path: string, init: RequestInit = {}, retry = true, notifyExpiration = true): Promise<Response> {
    const headers = new Headers(init.headers);
    if (this.accessToken) headers.set("Authorization", `Bearer ${this.accessToken}`);
    const response = await fetch(`${this.apiUrl}${path}`, { ...init, credentials: "include", headers });
    if (response.status !== 401) return response;
    if (!retry) {
      if (notifyExpiration) this.onSessionExpired();
      return response;
    }
    if (!await this.refresh()) {
      this.onSessionExpired();
      return response;
    }
    return this.request(path, init, false, notifyExpiration);
  }

  private async currentUser(): Promise<CurrentUser> {
    const response = await this.request("/api/v1/users/me", {}, false, false);
    if (!response.ok) throw new AuthError("expired");
    return response.json() as Promise<CurrentUser>;
  }

  private remember(tokens: TokenResponse) {
    this.accessToken = tokens.accessToken;
  }

  private async error(response: Response): Promise<AuthError> {
    if (response.status === 401) return new AuthError("credentials");
    if (response.status === 429) return new AuthError("rate-limit");
    return new AuthError("unexpected");
  }

  private withBrowserLock<T>(operation: () => Promise<T>): Promise<T> {
    const locks = typeof navigator === "undefined" ? undefined : navigator.locks;
    if (!locks) return operation();
    return new Promise<T>((resolve, reject) => {
      void locks.request("controle-gastos-auth-refresh", async () => {
        try {
          resolve(await operation());
        } catch (cause) {
          reject(cause);
        }
      }).catch(reject);
    });
  }
}
