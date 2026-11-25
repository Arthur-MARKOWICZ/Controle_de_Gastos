import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ReminderDialog } from "./ReminderDialog";
import { NOTIFICATION_PREFERENCES_PATH } from "../lib/api";
import { installFakeIpc, resetIpc } from "../lib/test-ipc";

afterEach(() => {
  cleanup();
  resetIpc();
});

const DESLIGADO = { dailyReminder: false, negativeBalance: false, sharedExpense: false, reminderTime: "20:00" };

function corpoDoPut(ipc: ReturnType<typeof installFakeIpc>) {
  const put = ipc.apiCalls().filter((call) => call.args.method === "PUT").at(-1);
  return put ? JSON.parse(String(put.args.body)) : null;
}

describe("lembrete diário no desktop", () => {
  it("avisa que só dispara com o aplicativo aberto", async () => {
    installFakeIpc().onApi(NOTIFICATION_PREFERENCES_PATH, { status: 200, body: DESLIGADO });
    render(<ReminderDialog onClose={vi.fn()} onSaved={vi.fn()} />);

    expect(await screen.findByText(/só dispara com o Verbas aberto/i)).toBeTruthy();
  });

  it("ativar pede a permissão e salva na API", async () => {
    const onSaved = vi.fn();
    const ipc = installFakeIpc()
      .on("reminder_permission", () => true)
      .onApi(NOTIFICATION_PREFERENCES_PATH, { status: 200, body: DESLIGADO })
      .onApi(NOTIFICATION_PREFERENCES_PATH, { status: 200, body: { ...DESLIGADO, reminderTime: "19:00" } })
      .onApi(NOTIFICATION_PREFERENCES_PATH, { status: 200, body: { ...DESLIGADO, dailyReminder: true, reminderTime: "19:00" } });
    render(<ReminderDialog onClose={vi.fn()} onSaved={onSaved} />);

    await userEvent.selectOptions(await screen.findByLabelText("Horário do lembrete"), "19:00");
    await userEvent.click(screen.getByRole("checkbox", { name: /Lembrar todo dia/i }));

    await waitFor(() => expect(onSaved).toHaveBeenLastCalledWith({ ...DESLIGADO, dailyReminder: true, reminderTime: "19:00" }));
    expect(ipc.calls.some((call) => call.command === "reminder_permission")).toBe(true);
    expect(corpoDoPut(ipc)).toEqual({ ...DESLIGADO, dailyReminder: true, reminderTime: "19:00" });
  });

  it("permissão negada mantém a escolha e explica", async () => {
    installFakeIpc()
      .on("reminder_permission", () => false)
      .onApi(NOTIFICATION_PREFERENCES_PATH, { status: 200, body: DESLIGADO })
      .onApi(NOTIFICATION_PREFERENCES_PATH, { status: 200, body: { ...DESLIGADO, dailyReminder: true } });
    render(<ReminderDialog onClose={vi.fn()} onSaved={vi.fn()} />);

    await userEvent.click(await screen.findByRole("checkbox", { name: /Lembrar todo dia/i }));

    expect(await screen.findByText(/notificações do sistema estão bloqueadas/i)).toBeTruthy();
  });

  it("falha ao salvar volta ao valor anterior", async () => {
    installFakeIpc()
      .on("reminder_permission", () => true)
      .onApi(NOTIFICATION_PREFERENCES_PATH, { status: 200, body: DESLIGADO })
      .onApi(NOTIFICATION_PREFERENCES_PATH, { status: 503, body: { detail: "Fora do ar" } });
    render(<ReminderDialog onClose={vi.fn()} onSaved={vi.fn()} />);

    const toggle = await screen.findByRole("checkbox", { name: /Lembrar todo dia/i });
    await userEvent.click(toggle);

    expect(await screen.findByRole("alert")).toBeTruthy();
    expect((screen.getByRole("checkbox", { name: /Lembrar todo dia/i }) as HTMLInputElement).checked).toBe(false);
  });
});
