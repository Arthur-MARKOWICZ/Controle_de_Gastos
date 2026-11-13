/**
 * Move o foco para o primeiro controle inválido depois de um submit rejeitado.
 * O `Field` já marca `aria-invalid` e associa a mensagem; aqui só levamos o
 * usuário até o campo que ele precisa corrigir.
 */
export function focusField(id: string) {
  if (typeof document === "undefined") return;
  const control = document.getElementById(id);
  if (control instanceof HTMLElement) control.focus();
}
