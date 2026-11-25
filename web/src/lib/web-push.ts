import type { WebPushSubscriptionDTO } from "./api";

/** Suporte a Web Push neste navegador (ADR-0033). */
export type PushSupport = "supported" | "unsupported" | "ios-needs-install";

export class PushPermissionDenied extends Error {
  constructor() {
    super("Permissão de notificação negada");
  }
}

const SERVICE_WORKER = "/sw.js";

function isIos(): boolean {
  const ua = navigator.userAgent ?? "";
  return /iPhone|iPad|iPod/.test(ua) || (/Macintosh/.test(ua) && (navigator.maxTouchPoints ?? 0) > 1);
}

export function pushSupport(): PushSupport {
  if (typeof window === "undefined" || typeof navigator === "undefined") return "unsupported";
  const apis = "serviceWorker" in navigator && "PushManager" in globalThis && "Notification" in globalThis;
  if (apis) return "supported";
  // No iOS 16.4+ as APIs só existem no app instalado na tela inicial.
  return isIos() ? "ios-needs-install" : "unsupported";
}

export function notificationPermission(): NotificationPermission | null {
  return "Notification" in globalThis ? Notification.permission : null;
}

function base64UrlToBytes(value: string): Uint8Array<ArrayBuffer> {
  const base64 = value.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(value.length / 4) * 4, "=");
  const binary = atob(base64);
  const bytes = new Uint8Array(new ArrayBuffer(binary.length));
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

async function existingSubscription(): Promise<PushSubscription | null> {
  if (typeof navigator === "undefined" || !("serviceWorker" in navigator)) return null;
  const registration = await navigator.serviceWorker.getRegistration("/");
  return registration ? registration.pushManager.getSubscription() : null;
}

/** Endpoint da assinatura deste navegador, se houver. */
export async function currentEndpoint(): Promise<string | null> {
  return (await existingSubscription())?.endpoint ?? null;
}

/** Pede a permissão (só após um clique) e assina com a chave pública do servidor. */
export async function subscribeThisBrowser(publicKey: string): Promise<WebPushSubscriptionDTO> {
  const permission = await Notification.requestPermission();
  if (permission !== "granted") throw new PushPermissionDenied();
  const registration = await navigator.serviceWorker.register(SERVICE_WORKER, { scope: "/" });
  const subscription = await registration.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: base64UrlToBytes(publicKey),
  });
  const json = subscription.toJSON() as { endpoint: string; keys: { p256dh: string; auth: string } };
  return { endpoint: json.endpoint, keys: { p256dh: json.keys.p256dh, auth: json.keys.auth } };
}

/** Cancela a assinatura deste navegador e devolve o endpoint, para a API também removê-lo. */
export async function unsubscribeThisBrowser(): Promise<string | null> {
  const subscription = await existingSubscription();
  if (!subscription) return null;
  await subscription.unsubscribe();
  return subscription.endpoint;
}
