package com.design.umlviewer.domain.mailbox;

import java.io.IOException;
import java.util.Map;

/**
 * Clean Architecture outbound port / gateway declaring mailbox operations. Decouples the domain
 * core from physical filesystem and serialization mechanisms.
 */
public interface MailboxGateway {

  MailboxEnvelope readMailbox(String projectRoot, boolean isToAgent);

  MailboxEnvelope.MailboxCommand appendCommand(
      String projectRoot,
      boolean isToAgent,
      String op,
      Map<String, Object> target,
      Map<String, Object> payload)
      throws IOException;

  MailboxEnvelope.MailboxCommand popOldest(String projectRoot, boolean isToAgent)
      throws IOException;
}
