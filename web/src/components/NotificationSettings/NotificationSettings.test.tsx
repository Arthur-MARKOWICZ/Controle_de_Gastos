import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { NotificationPreferencesDTO } from "../../lib/api";
import { PushPermissionDenied } from "../../lib/web-push";
import { NotificationSettings, type NotificationApi, type PushBrowser } from "./NotificationSettings";

afterEach(cleanup);

const DEFAULTS: NotificationPreferencesDTO = {
  dailyReminder: false,
  negativeBalance: false,
  sharedExpense: false,
  reminderTime: "20:00",
};

function setup({
  preferences = DEFAULTS,
  publicKey = "CHAVE" as string | null,
  support = "supported" as ReturnType<PushBrowser["support"]>,
  endpoint = null as string | null,
  subscribe = vi.fn().mockResolvedValue({ endpoint: "https://push.example.com/novo", keys: { p256dh: "P", auth: "A" } }),
  update = vi.fn().mockImplementation(async (body: NotificationPreferencesDTO) => body),
} = {}) {
  const api: NotificationApi = {
    getNotificationPreferences: vi.fn().mockResolvedValue(preferences),
    updateNotificationPreferences: update,
    getWebPushPublicKey: vi.fn().mockResolvedValue(publicKey),
    registerWebPushSubscription: vi.fn().mockResolvedValue(undefined),
    removeWebPushSubscription: vi.fn().mockResolvedValue(undefined),
  };
  const push: PushBrowser = {
    support: () => support,
    currentEndpoint: vi.fn().mockResolvedValue(endpoint),
    subscribe,
    unsubscribe: vi.fn().mockResolvedValue(endpoint),
  };
  render(<NotificationSettings api={api} push={push} />);
  return { api, push };
}

describe("NotificationSettings", () => {
  it("mostra os três tipos desligados e o horário padrão", async () => {
    setup();

    const daily = await screen.findByRole("checkbox", { name: /Lembrete diário/i });
    expect((daily as HTMLInputElement).checked).toBe(false);
    expect((screen.getByRole("checkbox", { name: /saldo negativo/i }) as HTMLInputElement).checked).toBe(false);
    expect((screen.getByRole("checkbox", { name: /verba compartilhada/i }) as HTMLInputElement).checked).toBe(false);
    expect((screen.getByLabelText(/Horário do lembrete/i) as HTMLSelectElement).value).toBe("20:00");
    expect(screen.getByText(/não mostram nomes de verbas nem valores/i)).toBeTruthy();
  });

  it("ativa o lembrete e escolhe o horário, salvando na API", async () => {
    const { api } = setup();

    fireEvent.click(await screen.findByRole("checkbox", { name: /Lembrete diário/i }));
    await waitFor(() => expect(api.updateNotificationPreferences).toHaveBeenCalledWith({ ...DEFAULTS, dailyReminder: true }));
    fireEvent.change(screen.getByLabelText(/Horário do lembrete/i), { target: { value: "21:00" } });

    await waitFor(() => expect(api.updateNotificationPreferences).toHaveBeenLastCalledWith({
      ...DEFAULTS, dailyReminder: true, reminderTime: "21:00",
    }));
    expect((screen.getByRole("checkbox", { name: /Lembrete diário/i }) as HTMLInputElement).checked).toBe(true);
    expect((screen.getByLabelText(/Horário do lembrete/i) as HTMLSelectElement).value).toBe("21:00");
  });

  it("oferece só horários em passos de 15 minutos", async () => {
    setup();

    const options = Array.from((await screen.findByLabelText(/Horário do lembrete/i) as HTMLSelectElement).options);
    expect(options).toHaveLength(96);
    expect(options.every((option) => /^\d{2}:(00|15|30|45)$/.test(option.value))).toBe(true);
  });

  it("volta ao valor anterior e avisa quando salvar falha", async () => {
    setup({ update: vi.fn().mockRejectedValue(new Error("rede")) });

    const negative = await screen.findByRole("checkbox", { name: /saldo negativo/i });
    fireEvent.click(negative);

    expect(await screen.findByText(/Não foi possível salvar/i)).toBeTruthy();
    expect((screen.getByRole("checkbox", { name: /saldo negativo/i }) as HTMLInputElement).checked).toBe(false);
  });

  it("ativa o push neste navegador depois da permissão", async () => {
    const { api, push } = setup();

    fireEvent.click(await screen.findByRole("button", { name: "Ativar neste navegador" }));

    await waitFor(() => expect(api.registerWebPushSubscription).toHaveBeenCalledWith({
      endpoint: "https://push.example.com/novo", keys: { p256dh: "P", auth: "A" },
    }));
    expect(push.subscribe).toHaveBeenCalledWith("CHAVE");
    expect(await screen.findByRole("button", { name: "Desativar neste navegador" })).toBeTruthy();
  });

  it("com permissão negada, não registra e explica como liberar", async () => {
    const { api } = setup({ subscribe: vi.fn().mockRejectedValue(new PushPermissionDenied()) });

    fireEvent.click(await screen.findByRole("button", { name: "Ativar neste navegador" }));

    expect(await screen.findByText(/bloqueadas para este site/i)).toBeTruthy();
    expect(api.registerWebPushSubscription).not.toHaveBeenCalled();
  });

  it("desativa neste navegador removendo a assinatura da API e do navegador", async () => {
    const { api, push } = setup({ endpoint: "https://push.example.com/atual" });

    fireEvent.click(await screen.findByRole("button", { name: "Desativar neste navegador" }));

    await waitFor(() => expect(api.removeWebPushSubscription).toHaveBeenCalledWith("https://push.example.com/atual"));
    expect(push.unsubscribe).toHaveBeenCalled();
    expect(await screen.findByRole("button", { name: "Ativar neste navegador" })).toBeTruthy();
  });

  it("no iOS sem instalação orienta a adicionar à tela inicial e mantém as preferências editáveis", async () => {
    setup({ support: "ios-needs-install" });

    expect(await screen.findByText(/Adicionar à Tela de Início/i)).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Ativar neste navegador" })).toBeNull();
    expect((screen.getByRole("checkbox", { name: /Lembrete diário/i }) as HTMLInputElement).disabled).toBe(false);
  });

  it("em navegador sem suporte, informa a limitação", async () => {
    setup({ support: "unsupported" });

    expect(await screen.findByText(/Este navegador não recebe notificações/i)).toBeTruthy();
  });

  it("sem chave no servidor, informa que o push está desligado", async () => {
    setup({ publicKey: null });

    expect(await screen.findByText(/desativadas neste servidor/i)).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Ativar neste navegador" })).toBeNull();
  });
});
