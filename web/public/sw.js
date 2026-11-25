/*
 * Service worker do Controle de gastos (ADR-0033).
 * Só exibe avisos de Web Push. Não intercepta requisições nem guarda nada em cache:
 * dados financeiros sempre vêm da rede.
 * O payload traz apenas categoria, tipo da verba e caminho; o texto é montado aqui.
 */
const TITLE = "Controle de gastos";
const ICON = "/apple-icon.png";

const PURPOSE_LABELS = {
  LIMIT: "Limite",
  GOAL: "Meta",
  FIXED: "Fixa",
  SAVINGS_TARGET: "Meta de poupança",
  ANNUAL_EXPENSE: "Gasto anual",
};

function messageFor(payload) {
  const purpose = PURPOSE_LABELS[payload && payload.purpose];
  switch (payload && payload.category) {
    case "DAILY_REMINDER":
      return "Nenhum lançamento hoje";
    case "NEGATIVE_BALANCE":
      return purpose ? `Você tem um alerta de verba do tipo ${purpose}` : "Você tem um alerta de verba";
    case "SHARED_EXPENSE":
      return purpose ? `Novo gasto em verba compartilhada do tipo ${purpose}` : "Novo gasto em verba compartilhada";
    default:
      return "Você tem um novo aviso";
  }
}

/** Só caminhos internos do app; qualquer outra coisa abre a página inicial. */
function safePath(path) {
  return typeof path === "string" && path.startsWith("/") && !path.startsWith("//") ? path : "/";
}

function readPayload(event) {
  try {
    return event.data ? event.data.json() : {};
  } catch {
    return {};
  }
}

self.addEventListener("install", () => {
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener("push", (event) => {
  const payload = readPayload(event);
  event.waitUntil(
    self.registration.showNotification(TITLE, {
      body: messageFor(payload),
      icon: ICON,
      tag: typeof payload.category === "string" ? payload.category : "aviso",
      data: { path: safePath(payload.path) },
    }),
  );
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const path = safePath(event.notification.data && event.notification.data.path);
  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((windows) => {
      const open = windows.find((client) => client.url.startsWith(self.location.origin));
      if (open) {
        return open.navigate(path).then(() => open.focus());
      }
      return self.clients.openWindow(path);
    }),
  );
});
