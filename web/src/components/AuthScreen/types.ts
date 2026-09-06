export type AuthScreenProps = {
  onLogin(email: string, password: string): Promise<void>;
  onRegister(email: string, password: string): Promise<void>;
  onOAuthLogin?(provider: "google" | "github"): void;
  externalError?: string | null;
  expired?: boolean;
};
