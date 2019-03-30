# Core Concept

The Imixs-Archive Service syncs the data of a workflow instance based on a so called 'Sync-Point'. A Sync-Point is a Time Stamp used to compare the last modified date of a snapshot. If a snapshot is newer than the last sync point, than the snapshot will be archived.
A syncpoint is defined by miliseconds preciction:

To select snapshots based on a given syncpoint the following JQOL query is used:

	SELECT document FROM Document AS document 
		WHERE document.modified > 'yyyy-MM-dd HH:mm:ss.SSS'
		AND document.type LIKE 'snapshot-%' 
		ORDER BY document.modified ASC
	
The syncpoint is always compared to the modified timestamp ($modified). This is to guarantie that a snapshot which was recovered by any kind of backup mechanism (e.g. the Imixs-Admin client) is verfied against the last syncpoint. Note: the $modified timestamp is a technical timestamp updated during each database update. If we would compare the $creation date a recovered snapshot would not be in the selection base on the last archive sync-point.

To avoid dupplicated synchronisation of a snapshot the syncService compares the $snapshotid with the existing archive data. If the snapshot is already part of the archive it will NOT be updated. This is a important concept to guarntie that after a desaster-recovery of a workflow instance the snapshot data is not overwritten in the archive. So the archive is a immutable snapshot of all workflow data. 	
