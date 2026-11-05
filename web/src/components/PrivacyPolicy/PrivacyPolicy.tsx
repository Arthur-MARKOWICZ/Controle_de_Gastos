import Link from "next/link";
import { TITULAR_CONTACT_EMAIL, TITULAR_CONTACT_MAILTO } from "../../privacy/titular-contact";
import styles from "./PrivacyPolicy.module.css";

export function PrivacyPolicy() {
  return (
    <main className={styles.page}>
      <article className={styles.article} aria-labelledby="privacy-title">
        <Link className={styles.brand} href="/" aria-label="Verbas, voltar ao início">
          <span aria-hidden="true">V</span> Verbas
        </Link>

        <p className={styles.eyebrow}>Privacidade</p>
        <h1 id="privacy-title">Política de privacidade</h1>
        <p className={styles.lead}>
          Esta página explica, em linguagem acessível, quais dados o Controle de Gastos
          (Verbas) trata, para quê e como você exerce seus direitos.
        </p>

        <p className={styles.banner} role="status">
          Texto em <strong>versão operacional</strong>, escrito pela engenharia com base no
          inventário de dados do projeto. Ainda não substitui parecer jurídico externo.
        </p>

        <section aria-labelledby="who-title">
          <h2 id="who-title">Quem controla os dados</h2>
          <p>
            O controlador é o operador pessoal deste sistema. Para pedidos sobre seus dados,
            use o canal eletrônico publicado abaixo.
          </p>
        </section>

        <section aria-labelledby="purposes-title">
          <h2 id="purposes-title">Para que usamos seus dados</h2>
          <ul>
            <li>
              <strong>Cadastro, autenticação e recuperação de acesso</strong> — e-mail, senha
              (apenas como hash), sessões, MFA/TOTP, códigos de recuperação e, se você optar,
              vínculo com Google ou GitHub.
            </li>
            <li>
              <strong>Renda, verbas e movimentações</strong> — valores em reais, nomes de
              verbas, natureza (limite, meta, compromisso, acumulação ou gasto anual),
              histórico de lançamentos e autoria técnica (UUID), para calcular saldos e
              relatórios.
            </li>
            <li>
              <strong>Segurança e abuso</strong> — contadores e identificadores técnicos
              derivados (sem gravar e-mail ou IP em texto claro nos logs de tentativa).
            </li>
            <li>
              <strong>Comunicações do serviço</strong> — e-mail de recuperação de senha
              entregue via Gmail SMTP do operador, somente quando solicitado.
            </li>
          </ul>
        </section>

        <section aria-labelledby="sharing-title">
          <h2 id="sharing-title">Com quem compartilhamos</h2>
          <p>
            Não vendemos seus dados. Operadores técnicos podem receber o mínimo necessário
            para o serviço funcionar:
          </p>
          <ul>
            <li>Google (Gmail SMTP) — entrega do link de recuperação de senha.</li>
            <li>
              Google e GitHub — somente se você escolher entrar ou vincular a conta via OAuth;
              o aplicativo não guarda o token de acesso do provedor além do necessário para
              concluir o login.
            </li>
            <li>
              Infraestrutura de hospedagem (VPS, PostgreSQL, HTTPS) — armazenamento e tráfego
              cifrado na borda, conforme a operação documentada do projeto.
            </li>
          </ul>
          <p>
            Compartilhamento de verbas entre usuários ainda não está disponível na API; quando
            existir, participantes verão apenas o necessário para a verba compartilhada.
          </p>
        </section>

        <section aria-labelledby="rights-title">
          <h2 id="rights-title">Seus direitos</h2>
          <p>
            Com a conta autenticada, você pode corrigir dados cadastrais (e-mail/senha/MFA),
            exportar e excluir a conta pelos fluxos já oferecidos no produto. Também pode
            pedir esclarecimentos ou atendimento pelo canal abaixo.
          </p>
          <p>
            Pedidos feitos por e-mail são registrados e respondidos conforme o procedimento
            interno de atendimento ao titular.
          </p>
        </section>

        <section aria-labelledby="contact-title">
          <h2 id="contact-title">Canal do titular</h2>
          <p>
            Contato eletrônico:{" "}
            <a href={TITULAR_CONTACT_MAILTO}>{TITULAR_CONTACT_EMAIL}</a>
          </p>
          <p className={styles.muted}>
            Use este endereço apenas para questões de privacidade e direitos do titular.
            Não envie senhas, códigos MFA nem dados financeiros desnecessários.
          </p>
        </section>

        <section aria-labelledby="retention-title">
          <h2 id="retention-title">Retenção e exclusão</h2>
          <p>
            Dados da conta e financeiros permanecem enquanto a conta existir, salvo prazos
            técnicos curtos (sessões, tokens, desafios MFA) descritos no inventário. A
            exclusão da conta remove o acesso ativo; cópias em backup seguem a política
            operacional até o ciclo de descarte.
          </p>
        </section>

        <p className={styles.updated}>Última atualização operacional: 5 de novembro de 2026.</p>

        <p className={styles.footerLinks}>
          <Link href="/">Voltar para entrar</Link>
        </p>
      </article>
    </main>
  );
}
