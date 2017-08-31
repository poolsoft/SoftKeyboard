package com.yiyun.softkeyboard;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import java.util.List;

/**
 * Created by guanqunzhang on 2017/8/17.
 */

public class CandidateViewContainer extends LinearLayout {

    private SoftKeyboard mService;

    public CandidateViewContainer(Context context) {
        super(context);
    }

    public CandidateViewContainer(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    public void setService(SoftKeyboard listener) {
        mService = listener;
    }

    public void setSuggestions(List<String> suggestions, boolean completions,
                               boolean typedWordValid) {}

    public void clear() {}
}
