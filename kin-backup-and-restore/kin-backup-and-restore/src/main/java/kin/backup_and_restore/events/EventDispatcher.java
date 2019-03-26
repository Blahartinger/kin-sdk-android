package kin.backup_and_restore.events;

import android.content.Intent;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import kin.backup_and_restore.BackupEvents;
import kin.backup_and_restore.RestoreEvents;

public interface EventDispatcher {


	@IntDef({BACKUP_EVENTS, RESTORE_EVENTS})
	@Retention(RetentionPolicy.SOURCE)
	@interface EventType {

	}

	int BACKUP_EVENTS = 0x00000001;
	int RESTORE_EVENTS = 0x00000002;

	String EXTRA_KEY_EVENT_TYPE = "EVENT_TYPE";
	String EXTRA_KEY_EVENT_ID = "EVENT_ID";

	void setBackupEvents(@Nullable BackupEvents backupEvents);

	void setRestoreEvents(@Nullable RestoreEvents restoreEvents);

	void sendEvent(@EventType final int eventType, final int eventID);

	void setActivityResult(int resultCode, Intent data);

	void unregister();
}