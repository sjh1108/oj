package dev.algoj.domain.problem.repository;

/**
 * Projection for the admin test-case list: sizes and a short head of the data
 * instead of the full LONGTEXT. A generated case can be several MB, and listing
 * a problem's cases used to drag every byte through the server into the browser.
 */
public interface TestCaseSummary {

    Long getId();

    Integer getOrderIndex();

    Boolean getIsSample();

    Boolean getIsDraft();

    Long getInputLength();

    Long getExpectedOutputLength();

    String getInputPreview();

    String getExpectedOutputPreview();
}
