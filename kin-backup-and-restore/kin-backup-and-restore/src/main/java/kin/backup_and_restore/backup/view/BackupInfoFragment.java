package kin.backup_and_restore.backup.view;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import kin.backup_and_restore.R;
import kin.backup_and_restore.backup.presenter.BackupInfoPresenter;
import kin.backup_and_restore.backup.presenter.BackupInfoPresenterImpl;
import kin.backup_and_restore.base.BaseView;
import kin.backup_and_restore.events.BroadcastManagerImpl;
import kin.backup_and_restore.events.CallbackManager;
import kin.backup_and_restore.events.EventDispatcherImpl;

public class BackupInfoFragment extends Fragment implements BaseView {

	public static BackupInfoFragment newInstance(@NonNull final BackupNavigator nextStepListener) {
		BackupInfoFragment fragment = new BackupInfoFragment();
		fragment.setNextStepListener(nextStepListener);
		return fragment;
	}

	private BackupNavigator nextStepListener;
	private BackupInfoPresenter backupInfoPresenter;


	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
		@Nullable Bundle savedInstanceState) {
		View root = inflater.inflate(R.layout.kinrecovery_fragment_backup_info, container, false);
		initViews(root);
		backupInfoPresenter = new BackupInfoPresenterImpl(
			new CallbackManager(new EventDispatcherImpl(new BroadcastManagerImpl(getActivity()))), nextStepListener);
		backupInfoPresenter.onAttach(this);
		return root;
	}

	private void initViews(View root) {
		root.findViewById(R.id.lets_go_button).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				backupInfoPresenter.letsGoButtonClicked();
			}
		});
	}

	public void setNextStepListener(@NonNull final BackupNavigator nextStepListener) {
		this.nextStepListener = nextStepListener;
	}
}