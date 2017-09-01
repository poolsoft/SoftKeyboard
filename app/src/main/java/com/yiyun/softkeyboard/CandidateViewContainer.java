package com.yiyun.softkeyboard;

import android.content.Context;
import android.media.ImageWriter;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Created by guanqunzhang on 2017/8/17.
 */

public class CandidateViewContainer extends LinearLayout {

    private SoftKeyboard mService;
    private TextView mComposingView;
    private CandidateView mCandidateView;
    private RelativeLayout mCandidateViewLayout;
    private RelativeLayout mToolbarViewLayout;
    private ImageButton mLogoButton;
    private ImageButton mCloseKbButton;
    private ImageButton mShowHideMoreCandsButton;

    public CandidateViewContainer(Context context) {
        super(context);
    }

    public CandidateViewContainer(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    public void initViews() {
        mComposingView = (TextView) findViewById(R.id.composing_view);
        mCandidateView = (CandidateView) findViewById(R.id.candidate_view);
        mCandidateViewLayout = (RelativeLayout) findViewById(R.id.candidate_view_layout);
        mToolbarViewLayout = (RelativeLayout) findViewById(R.id.toolbar_view_layout);
        mLogoButton = (ImageButton) findViewById(R.id.logo_btn);
        mCloseKbButton = (ImageButton) findViewById(R.id.close_kb_btn);
        mShowHideMoreCandsButton = (ImageButton) findViewById(R.id.show_hide_more_cands_btn);
    }

    public void setService(SoftKeyboard listener) {
        mService = listener;
        mCandidateView.setService(listener);
    }

    public void setSuggestions(List<String> suggestions, boolean completions, boolean typedWordValid) {
        mCandidateView.setSuggestions(suggestions, completions, typedWordValid);
    }

    public void setComposingText(String composingText) {
        mComposingView.setText(composingText);
    }

    public void clear() {
        mCandidateView.clear();
    }

    public void setCandidateViewShown(Boolean show) {
        if (show) {
            mCandidateViewLayout.setVisibility(VISIBLE);
            mToolbarViewLayout.setVisibility(GONE);
        } else {
            mCandidateViewLayout.setVisibility(GONE);
            mToolbarViewLayout.setVisibility(VISIBLE);
        }
    }
}
