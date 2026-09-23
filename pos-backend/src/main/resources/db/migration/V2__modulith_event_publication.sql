CREATE TABLE IF NOT EXISTS event_publication (
  id BINARY(16) NOT NULL,
  listener_id VARCHAR(512) NOT NULL,
  event_type VARCHAR(512) NOT NULL,
  serialized_event VARCHAR(4000) NOT NULL,
  publication_date TIMESTAMP(6) NOT NULL,
  completion_date TIMESTAMP(6) NULL,
  status VARCHAR(20),
  completion_attempts INT,
  last_resubmission_date TIMESTAMP(6) NULL,
  PRIMARY KEY (id),
  INDEX event_publication_by_completion_date_idx (completion_date)
) ENGINE = InnoDB;
