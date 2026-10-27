package app.watchmark.catalog;

/**
 * Provider-neutral production status. {@link #ENDED} and {@link #CANCELED} mean
 * no further episodes are expected (used by status automation, rule R9).
 */
public enum ShowStatus {

	RETURNING, IN_PRODUCTION, PLANNED, PILOT, ENDED, CANCELED, UNKNOWN;

	public boolean isFinished() {
		return this == ENDED || this == CANCELED;
	}

}
