package org.telegram.ui.Components;

import android.content.DialogInterface;

import androidx.annotation.NonNull;
import androidx.mediarouter.app.MediaRouteChooserDialogFragment;

import org.telegram.messenger.CastManager;
import org.telegram.messenger.MediaController;

public class CancelableMediaRouteChooserDialogFragment extends MediaRouteChooserDialogFragment {
    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);

        if (!CastManager.isCasting()) {
            MediaController.getInstance().setAboutToCastAudio(false);
            MediaController.getInstance().setAboutToCastVideo(false);
        }
    }
}
