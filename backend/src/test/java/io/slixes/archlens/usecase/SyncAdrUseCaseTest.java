package io.slixes.archlens.usecase;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SyncAdrUseCaseTest {

  @Test
  void testRecordDipInversionCreatesAdrDocument(@TempDir Path tempDir) throws IOException {
    SyncAdrUseCase useCase = new SyncAdrUseCase();

    SyncAdrUseCase.AdrDocument doc1 =
        useCase.recordDipInversion(
            tempDir.toString(),
            "com.example.domain.OrderService",
            "com.example.adapter.PostgresRepo",
            "OrderRepositoryPort",
            "Domain core must not import concrete database adapter.");

    assertNotNull(doc1);
    assertEquals(1, doc1.index());
    assertEquals(
        "0001-invert-outward-dependency-from-orderservice-using-orderrepos.md", doc1.filename());
    assertEquals("Accepted", doc1.status());

    File adr1 = new File(tempDir.toFile(), "docs/adr/" + doc1.filename());
    assertTrue(adr1.exists());
    String content1 = Files.readString(adr1.toPath());
    assertTrue(
        content1.contains(
            "# 1. Invert outward dependency from OrderService using OrderRepositoryPort"));
    assertTrue(content1.contains("OrderRepositoryPort"));
    assertTrue(content1.contains("OrderService"));

    // Record second ADR to check incrementing index
    SyncAdrUseCase.AdrDocument doc2 =
        useCase.recordPackageReorganization(
            tempDir.toString(),
            "Migrate to Package-by-Feature Billing Domain",
            "Scattered technical layers reduced screaming architecture score.",
            "Group billing controllers, services, and models into com.example.billing.",
            "High cohesion, explicit bounded context.");

    assertNotNull(doc2);
    assertEquals(2, doc2.index());
    assertEquals("0002-migrate-to-package-by-feature-billing-domain.md", doc2.filename());
    File adr2 = new File(tempDir.toFile(), "docs/adr/" + doc2.filename());
    assertTrue(adr2.exists());
  }
}
