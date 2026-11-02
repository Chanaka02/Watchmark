package app.watchmark.tracking;

/** The kinds of undoable tracking actions (rule R3). */
public enum OperationType {

	/** One episode marked watched. */
	WATCH,
	/** One episode marked unwatched. */
	UNWATCH,
	/** The eligible episodes of a season marked watched. */
	SEASON,
	/** Every eligible episode up to a given one marked watched. */
	UP_TO

}
