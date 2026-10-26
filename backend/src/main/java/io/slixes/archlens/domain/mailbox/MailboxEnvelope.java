package io.slixes.archlens.domain.mailbox;

import java.util.List;
import java.util.Map;

public record MailboxEnvelope(long nextId, List<MailboxCommand> queue) {
  public record MailboxCommand(
      long id, String op, Map<String, Object> target, Map<String, Object> payload) {}
}
