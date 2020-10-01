package com.yiyun.softkeyboard;

import android.app.Dialog;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.os.IBinder;
import android.text.InputType;
import android.text.method.MetaKeyKeyListener;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import java.util.ArrayList;
import java.util.List;

public class SoftKeyboard extends InputMethodService implements CustomKeyboardView.OnKeyboardActionListener {

    static final boolean DEBUG = true;

    /**
     * This boolean indicates the optional example code for performing
     * processing of hard keys in addition to regular text generation
     * from on-screen interaction.  It would be used for input methods that
     * perform language translations (such as converting text entered on
     * a QWERTY keyboard to Chinese), but may not be used for input methods
     * that are primarily intended to be used for on-screen text entry.
     */
    static final boolean PROCESS_HARD_KEYS = true;

    private InputMethodManager mInputMethodManager;

    private CompletionInfo[] mCompletions;

    private StringBuilder mComposing = new StringBuilder();
    private boolean mPredictionOn;
    private boolean mCompletionOn;
    private int mLastDisplayWidth;
    private boolean mCapsLock;
    private long mLastShiftTime;
    private long mMetaState;

    private int mTransKeyState = 0;

    private int mEditorInfo;

    private CustomKeyboardView mInputView;
    private CandidateViewContainer mCandidateViewContainer;

    private CustomKeyboard mSymbolsKeyboard;
    private CustomKeyboard mSymbolsMoreKeyboard;
    private CustomKeyboard mSymbolsKeyboardCh;
    private CustomKeyboard mSymbolsMoreKeyboardCh;
    private CustomKeyboard mEngKeyboard;
    private CustomKeyboard mPinyinKeyboard;
    private CustomKeyboard mPotKeyboard;

    private List<String> candidateWordList;

    /** mEngKeyboard or mPinyinKeyboard or mPotKeyboard */
    private CustomKeyboard mCurKeyboard;

    private String mWordSeparators;

    /**
     * Main initialization of the input method component.  Be sure to call
     * to super class.
     */
    @Override public void onCreate() {
        super.onCreate();
        mInputMethodManager = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        mWordSeparators = getResources().getString(R.string.word_separator_space/*word_separators*/);
    }

    /**
     * This is the point where you can do all of your UI initialization.  It
     * is called after creation and any configuration change.
     */
    @Override public void onInitializeInterface() {
        if (mEngKeyboard != null) {
            // Configuration changes can happen after the keyboard gets recreated,
            // so we need to be able to re-build the keyboards if the available
            // space has changed.
            int displayWidth = getMaxWidth();
            if (displayWidth == mLastDisplayWidth) return;
            mLastDisplayWidth = displayWidth;
        }

        mEngKeyboard = new CustomKeyboard(this, R.xml.keyboard_en_qwerty);
        mPinyinKeyboard = new CustomKeyboard(this, R.xml.keyboard_pinyin_qwerty);
        mPotKeyboard = new CustomKeyboard(this, R.xml.keyboard_pt_qwerty);
        mSymbolsKeyboard = new CustomKeyboard(this, R.xml.keyboard_symbols);
        mSymbolsMoreKeyboard = new CustomKeyboard(this, R.xml.keyboard_symbols_more);
        mSymbolsKeyboardCh = new CustomKeyboard(this, R.xml.keyboard_ch_symbols);
        mSymbolsMoreKeyboardCh = new CustomKeyboard(this, R.xml.keyboard_ch_symbols_more);

        candidateWordList = new ArrayList<String>();
    }

    /**
     * Called by the framework when your view for creating input needs to
     * be generated.  This will be called the first time your input method
     * is displayed, and every time it needs to be re-created such as due to
     * a configuration change.
     */
    @Override public View onCreateInputView() {
        mInputView = (CustomKeyboardView) getLayoutInflater().inflate(R.layout.input, null);
        mInputView.setOnKeyboardActionListener(this);
        setKeyboard(mPinyinKeyboard);
        return mInputView;
    }

    private void setKeyboard(CustomKeyboard nextKeyboard) {
        CustomKeyboard.LanguageType languageType = CustomKeyboard.LanguageType.TYPE_EN;
        if (mCurKeyboard == mPinyinKeyboard) {
            languageType = CustomKeyboard.LanguageType.TYPE_CH;
        } else if (mCurKeyboard == mPotKeyboard) {
            languageType = CustomKeyboard.LanguageType.TYPE_PT;
        }
        Drawable translateKeyIcon = getResources().getDrawable(R.drawable.icon_trans_yuan);
        if (mTransKeyState == 1) {
            translateKeyIcon = getResources().getDrawable(R.drawable.icon_trans_yi);
        } else if (mTransKeyState == 2) {
            translateKeyIcon = getResources().getDrawable(R.drawable.icon_trans_shuang);
        }
        nextKeyboard.configKeyboard(getResources(), mEditorInfo, languageType, translateKeyIcon);
        mInputView.setKeyboard(nextKeyboard);
    }

    /**
     * Called by the framework when your view for showing candidates needs to
     * be generated, like {@link #onCreateInputView}.
     */
    @Override public View onCreateCandidatesView() {
//        mCandidateView = new CandidateView(this);
        mCandidateViewContainer = (CandidateViewContainer) getLayoutInflater().inflate(R.layout.candidate_view, null);
        mCandidateViewContainer.initViews();
        mCandidateViewContainer.setService(this);
        setCandidatesViewShown(true);
        return mCandidateViewContainer;
    }

    /**
     * This is the main point where we do our initialization of the input method
     * to begin operating on an application.  At this point we have been
     * bound to the client, and are now receiving all of the detailed information
     * about the target of our edits.
     */
    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);

        // Reset our state.  We want to do this even if restarting, because
        // the underlying state of the text editor could have changed in any way.
        mComposing.setLength(0);
        updateCandidates();

        if (!restarting) {
            // Clear shift states.
            mMetaState = 0;
        }

        mPredictionOn = false;
        mCompletionOn = false;
        mCompletions = null;

        // We are now going to initialize our state based on the type of
        // text being edited.
        switch (attribute.inputType & InputType.TYPE_MASK_CLASS) {
            case InputType.TYPE_CLASS_NUMBER:
            case InputType.TYPE_CLASS_DATETIME:
                // Numbers and dates default to the keyboard_symbols keyboard, with
                // no extra features.
                mCurKeyboard = mSymbolsKeyboard;
                break;

            case InputType.TYPE_CLASS_PHONE:
                // Phones will also default to the keyboard_symbols keyboard, though
                // often you will want to have a dedicated phone keyboard.
                mCurKeyboard = mSymbolsKeyboard;
                break;

            case InputType.TYPE_CLASS_TEXT:
                // This is general text editing.  We will default to the
                // normal alphabetic keyboard, and assume that we should
                // be doing predictive text (showing candidates as the
                // user types).
                mCurKeyboard = mPinyinKeyboard;
                mPredictionOn = true;

                // We now look for a few special variations of text that will
                // modify our behavior.
                int variation = attribute.inputType & InputType.TYPE_MASK_VARIATION;
                if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                    // Do not display predictions / what the user is typing
                    // when they are entering a password.
                    mPredictionOn = false;
                }

                if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                        || variation == InputType.TYPE_TEXT_VARIATION_URI
                        || variation == InputType.TYPE_TEXT_VARIATION_FILTER) {
                    // Our predictions are not useful for e-mail addresses
                    // or URIs.
                    mPredictionOn = false;
                }

                if ((attribute.inputType & InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                    // If this is an auto-complete text view, then our predictions
                    // will not be shown and instead we will allow the editor
                    // to supply their own.  We only show the editor's
                    // candidates when in fullscreen mode, otherwise relying
                    // own it displaying its own UI.
                    mPredictionOn = false;
                    mCompletionOn = isFullscreenMode();
                }

                // We also want to look at the current state of the editor
                // to decide whether our alphabetic keyboard should start out
                // shifted.
                updateShiftKeyState(attribute);
                break;

            default:
                // For all unknown input types, default to the alphabetic
                // keyboard with no special features.
                mCurKeyboard = mPinyinKeyboard;
                updateShiftKeyState(attribute);
        }

        // Update the label on the enter key, depending on what the application
        // says it will do.
        //mCurKeyboard.setImeOptions(getResources(), attribute.imeOptions);
        mEditorInfo = attribute.imeOptions;
    }

    /**
     * This is called when the user is done editing a field.  We can use
     * this to reset our state.
     */
    @Override public void onFinishInput() {
        super.onFinishInput();

        // Clear current composing text and candidates.
        mComposing.setLength(0);
        updateCandidates();

        // We only hide the candidates window when finishing input on
        // a particular editor, to avoid popping the underlying application
        // up and down if the user is entering text into the bottom of
        // its window.
//        setCandidatesViewShown(false);

        mCurKeyboard = mEngKeyboard;
        if (mInputView != null) {
            mInputView.closing();
        }
    }

    @Override public void onStartInputView(EditorInfo attribute, boolean restarting) {
        super.onStartInputView(attribute, restarting);
        // Apply the selected keyboard to the input view.
        setKeyboard(mCurKeyboard);
        mInputView.closing();
        final InputMethodSubtype subtype = mInputMethodManager.getCurrentInputMethodSubtype();
        //mInputView.setSubtypeOnSpaceKey(subtype);
    }

    @Override
    public void onCurrentInputMethodSubtypeChanged(InputMethodSubtype subtype) {
        //mInputView.setSubtypeOnSpaceKey(subtype);
        Log.e("input","onCurrentInput");
    }

    /**
     * Deal with the editor reporting movement of its cursor.
     */
    @Override public void onUpdateSelection(int oldSelStart, int oldSelEnd,
                                            int newSelStart, int newSelEnd,
                                            int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);

        // If the current selection in the text view changes, we should
        // clear whatever candidate text we have.
        if (mComposing.length() > 0 && (newSelStart != candidatesEnd
                || newSelEnd != candidatesEnd)) {
            mComposing.setLength(0);
            updateCandidates();
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.finishComposingText();
            }
        }
    }

    /**
     * This tells us about completions that the editor has determined based
     * on the current text in it.  We want to use this in fullscreen mode
     * to show the completions ourself, since the editor can not be seen
     * in that situation.
     */
    @Override public void onDisplayCompletions(CompletionInfo[] completions) {
        if (mCompletionOn) {
            mCompletions = completions;
            if (completions == null) {
                setSuggestions(null, false, false);
                return;
            }

            List<String> stringList = new ArrayList<String>();
            for (int i = 0; i < completions.length; i++) {
                CompletionInfo ci = completions[i];
                if (ci != null) stringList.add(ci.getText().toString());
            }
            setSuggestions(stringList, true, true);
        }
    }

    /**
     * This translates incoming hard key events in to edit operations on an
     * InputConnection.  It is only needed when using the
     * PROCESS_HARD_KEYS option.
     */
    private boolean translateKeyDown(int keyCode, KeyEvent event) {
        mMetaState = MetaKeyKeyListener.handleKeyDown(mMetaState,
                keyCode, event);
        int c = event.getUnicodeChar(MetaKeyKeyListener.getMetaState(mMetaState));
        mMetaState = MetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState);
        InputConnection ic = getCurrentInputConnection();
        if (c == 0 || ic == null) {
            return false;
        }

        boolean dead = false;

        if ((c & KeyCharacterMap.COMBINING_ACCENT) != 0) {
            dead = true;
            c = c & KeyCharacterMap.COMBINING_ACCENT_MASK;
        }

        if (mComposing.length() > 0) {
            char accent = mComposing.charAt(mComposing.length() -1 );
            int composed = KeyEvent.getDeadChar(accent, c);

            if (composed != 0) {
                c = composed;
                mComposing.setLength(mComposing.length()-1);
            }
        }

        onKey(c, null, "");

        return true;
    }

    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                // The InputMethodService already takes care of the back
                // key for us, to dismiss the input method if it is shown.
                // However, our keyboard could be showing a pop-up window
                // that back should dismiss, so we first allow it to do that.
                if (event.getRepeatCount() == 0 && mInputView != null) {
                    if (mInputView.handleBack()) {
                        return true;
                    }
                }
                break;

            case KeyEvent.KEYCODE_DEL:
                // Special handling of the delete key: if we currently are
                // composing text for the user, we want to modify that instead
                // of let the application to the delete itself.
                if (mComposing.length() > 0) {
                    onKey(Keyboard.KEYCODE_DELETE, null, "");
                    return true;
                }
                break;

            case KeyEvent.KEYCODE_ENTER:
                // Let the underlying text editor always handle these.
                return false;

            default:
                // For all other keys, if we want to do transformations on
                // text being entered with a hard keyboard, we need to process
                // it and do the appropriate action.
                if (PROCESS_HARD_KEYS) {
                    if (keyCode == KeyEvent.KEYCODE_SPACE
                            && (event.getMetaState()&KeyEvent.META_ALT_ON) != 0) {
                        // A silly example: in our input method, Alt+Space
                        // is a shortcut for 'android' in lower case.
                        InputConnection ic = getCurrentInputConnection();
                        if (ic != null) {
                            // First, tell the editor that it is no longer in the
                            // shift state, since we are consuming this.
                            ic.clearMetaKeyStates(KeyEvent.META_ALT_ON);
                            keyDownUp(KeyEvent.KEYCODE_A);
                            keyDownUp(KeyEvent.KEYCODE_N);
                            keyDownUp(KeyEvent.KEYCODE_D);
                            keyDownUp(KeyEvent.KEYCODE_R);
                            keyDownUp(KeyEvent.KEYCODE_O);
                            keyDownUp(KeyEvent.KEYCODE_I);
                            keyDownUp(KeyEvent.KEYCODE_D);
                            // And we consume this event.
                            return true;
                        }
                    }
                    if (mPredictionOn && translateKeyDown(keyCode, event)) {
                        return true;
                    }
                }
        }

        return super.onKeyDown(keyCode, event);
    }

    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        // If we want to do transformations on text being entered with a hard
        // keyboard, we need to process the up events to update the meta key
        // state we are tracking.
        if (PROCESS_HARD_KEYS) {
            if (mPredictionOn) {
                mMetaState = MetaKeyKeyListener.handleKeyUp(mMetaState,
                        keyCode, event);
            }
        }

        return super.onKeyUp(keyCode, event);
    }

    /**
     * Helper function to commit any text being composed in to the editor.
     */
    private void commitTyped(InputConnection inputConnection) {
        if (mComposing.length() > 0) {
            inputConnection.commitText(mComposing, mComposing.length());
            mComposing.setLength(0);
            updateCandidates();
        }
    }

    /**
     * Helper to update the shift state of our keyboard based on the initial
     * editor state.
     */
    private void updateShiftKeyState(EditorInfo attr) {
        if (attr != null
                && mInputView != null && mEngKeyboard == mInputView.getKeyboard()) {
            int caps = 0;
            EditorInfo ei = getCurrentInputEditorInfo();
            if (ei != null && ei.inputType != InputType.TYPE_NULL) {
                caps = getCurrentInputConnection().getCursorCapsMode(attr.inputType);
            }
            mInputView.setShifted(mCapsLock || caps != 0);
        }
    }

    /**
     * Helper to determine if a given character code is alphabetic.
     */
    private boolean isAlphabet(int code) {
        if (Character.isLetter(code)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Helper to send a key down / key up pair to the current editor.
     */
    private void keyDownUp(int keyEventCode) {
        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
    }

    /**
     * Helper to send a character to the editor as raw key events.
     */
    private void sendKey(int keyCode) {
        switch (keyCode) {
            case '\n':
                keyDownUp(KeyEvent.KEYCODE_ENTER);
                break;
            default:
                if (keyCode >= '0' && keyCode <= '9') {
                    keyDownUp(keyCode - '0' + KeyEvent.KEYCODE_0);
                } else {
                    getCurrentInputConnection().commitText(String.valueOf((char) keyCode), 1);
                }
                break;
        }
    }

    // Implementation of KeyboardViewListener
    public void onKey(int primaryCode, int[] keyCodes, CharSequence label) {
        if (isWordSeparator(primaryCode)) {
            // Handle separator
            if (mComposing.length() > 0) {
                commitTyped(getCurrentInputConnection());
            }
            sendKey(primaryCode);
            updateShiftKeyState(getCurrentInputEditorInfo());
        } else if (primaryCode == KeyCode.KEYCODE_ENTER) {
            handleEnter();
        } else if (primaryCode == KeyCode.KEYCODE_DELETE) {
            handleBackspace();
        } else if (primaryCode == KeyCode.KEYCODE_SHIFT) {
            handleShift();
        } else if (primaryCode == KeyCode.KEYCODE_CANCEL) {
            handleClose();
        } else if (primaryCode == KeyCode.KEYCODE_MODE_CHANGE && mInputView != null) {
            handleSymbolKeyboardChange();
        } else if (primaryCode == KeyCode.KEYCODE_LANGUAGE_CHANGE && mInputView != null) {
            handleLanguageSwitch();
        } else if (primaryCode == KeyCode.KEYCODE_TRANSLATE) {
            handleTranslateKey();
        } else if (primaryCode == KeyCode.KEYCODE_BAN_QUAN_JIAO_SWITCH) {
            handleBanQuanSwitch();
        } else {
            handleCharacter(primaryCode, keyCodes, label);
        }
    }

    public void onText(CharSequence text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();
        if (mComposing.length() > 0) {
            commitTyped(ic);
        }
        ic.commitText(text, 0);
        ic.endBatchEdit();
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    /**
     * Update the list of available candidates from the current composing
     * text.  This will need to be filled in by however you are determining
     * candidates.
     */
    private void updateCandidates() {
        if (!mCompletionOn) {
            if (mComposing.length() > 0) {
//                candidateWordList.add(mComposing.toString());
                candidateWordList.add(0, mComposing.toString());
                setSuggestions(candidateWordList, true, true);
                if (mCandidateViewContainer != null) {
                    mCandidateViewContainer.setCandidateViewShown(true);
                    mCandidateViewContainer.setComposingText(mComposing.toString());
                }
            } else {
                candidateWordList.clear();
                setSuggestions(null, false, false);
                if (mCandidateViewContainer != null) {
                    mCandidateViewContainer.setCandidateViewShown(false);
                    mCandidateViewContainer.setComposingText(mComposing.toString());
                }
            }
        }
    }

    public void setSuggestions(List<String> suggestions, boolean completions,
                               boolean typedWordValid) {
        if (suggestions != null && suggestions.size() > 0) {
            setCandidatesViewShown(true);
        } else if (isExtractViewShown()) {
            setCandidatesViewShown(true);
        }
        if (mCandidateViewContainer != null) {
            mCandidateViewContainer.setSuggestions(suggestions, completions, typedWordValid);
        }
    }

    private void handleLanguageSwitch() {
        //mInputMethodManager.switchToNextInputMethod(getToken(), false /* onlyCurrentIme */);
        CustomKeyboard current = mInputView.getKeyboard();
        if (current == mEngKeyboard) {
            mCurKeyboard = mPinyinKeyboard;
        } else {
            mCurKeyboard = mEngKeyboard;
        }
        setKeyboard(mCurKeyboard);
    }

    private void handleBanQuanSwitch() {
        CustomKeyboard current = mInputView.getKeyboard();
        if (current == mSymbolsKeyboard) {
            setKeyboard(mSymbolsKeyboardCh);
        } else if (current == mSymbolsMoreKeyboard) {
            setKeyboard(mSymbolsMoreKeyboardCh);
        } else if (current == mSymbolsKeyboardCh) {
            setKeyboard(mSymbolsKeyboard);
        } else if (current == mSymbolsMoreKeyboardCh) {
            setKeyboard(mSymbolsMoreKeyboard);
        }
    }

    private void handleSymbolKeyboardChange() {
        CustomKeyboard current = mInputView.getKeyboard();
        if (current == mSymbolsKeyboard ||
                current == mSymbolsMoreKeyboard ||
                current == mSymbolsKeyboardCh ||
                current == mSymbolsMoreKeyboardCh) {
            setKeyboard(mCurKeyboard);
        } else {
            mSymbolsKeyboard.setShifted(false);
            setKeyboard(mSymbolsKeyboard);
        }
    }

    private void handleTranslateKey() {
        switch (mTransKeyState) {
            case 0:
                mTransKeyState = 1;
                mInputView.setTranslateKeyIcon(getResources().getDrawable(R.drawable.icon_trans_yi));
                break;
            case 1:
                mTransKeyState = 2;
                mInputView.setTranslateKeyIcon(getResources().getDrawable(R.drawable.icon_trans_shuang));
                break;
            case 2:
                mTransKeyState = 0;
                mInputView.setTranslateKeyIcon(getResources().getDrawable(R.drawable.icon_trans_yuan));
                break;

            default:
                break;
        }
    }

    private void handleBackspace() {
        final int length = mComposing.length();
        if (length > 1) {
            mComposing.delete(length - 1, length);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            updateCandidates();
        } else if (length > 0) {
            mComposing.setLength(0);
            getCurrentInputConnection().commitText("", 0);
            updateCandidates();
        } else {
            keyDownUp(KeyEvent.KEYCODE_DEL);
        }
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    private void handleEnter() {
        keyDownUp(KeyEvent.KEYCODE_ENTER);
    }

    private void handleShift() {
        if (mInputView == null) {
            return;
        }
        CustomKeyboard currentKeyboard = mInputView.getKeyboard();
        if (currentKeyboard == mEngKeyboard || currentKeyboard == mPinyinKeyboard || currentKeyboard == mPotKeyboard) {
            // Alphabet keyboard
            checkToggleCapsLock();
            mInputView.setShifted(mCapsLock || !mInputView.isShifted());
        } else if (currentKeyboard == mSymbolsKeyboard) {
            mSymbolsKeyboard.setShifted(true);
            setKeyboard(mSymbolsMoreKeyboard);
            mSymbolsMoreKeyboard.setShifted(true);
        } else if (currentKeyboard == mSymbolsMoreKeyboard) {
            mSymbolsMoreKeyboard.setShifted(false);
            setKeyboard(mSymbolsKeyboard);
            mSymbolsKeyboard.setShifted(false);
        } else if (currentKeyboard == mSymbolsKeyboardCh) {
            mSymbolsKeyboardCh.setShifted(true);
            setKeyboard(mSymbolsMoreKeyboardCh);
            mSymbolsMoreKeyboardCh.setShifted(true);
        } else if (currentKeyboard == mSymbolsMoreKeyboardCh) {
            mSymbolsMoreKeyboardCh.setShifted(false);
            setKeyboard(mSymbolsKeyboardCh);
            mSymbolsKeyboardCh.setShifted(false);
        }
    }

    private void handleCharacter(int primaryCode, int[] keyCodes, CharSequence label) {
        Log.d("[SoftKeyboard]", "handleCharacter "+label);
        if (isInputViewShown()) {
            if (mInputView.isShifted()) {
                primaryCode = Character.toUpperCase(primaryCode);
            }
        }
        if (isAlphabet(primaryCode) && mPredictionOn) {
            Log.d("[SoftKeyboard]", "mPredictionOn");
            mComposing.append((char) primaryCode);
//            getCurrentInputConnection().setComposingText(mComposing, 1);
            updateShiftKeyState(getCurrentInputEditorInfo());
            updateCandidates();
        } else {
            //getCurrentInputConnection().commitText(String.valueOf((char) primaryCode), 1);
            getCurrentInputConnection().commitText(label, 1);
        }
    }

    private void handleClose() {
        commitTyped(getCurrentInputConnection());
        requestHideSelf(0);
        mInputView.closing();
    }

    private IBinder getToken() {
        final Dialog dialog = getWindow();
        if (dialog == null) {
            return null;
        }
        final Window window = dialog.getWindow();
        if (window == null) {
            return null;
        }
        return window.getAttributes().token;
    }

    private void checkToggleCapsLock() {
//        long now = System.currentTimeMillis();
//        if (mLastShiftTime + 800 > now) {
//            mCapsLock = !mCapsLock;
//            mLastShiftTime = 0;
//        } else {
//            mLastShiftTime = now;
//        }
        mCapsLock = !mCapsLock;
    }

    private String getWordSeparators() {
        return mWordSeparators;
    }

    public boolean isWordSeparator(int code) {
        String separators = getWordSeparators();
        return separators.contains(String.valueOf((char)code));
    }

    public void pickDefaultCandidate() {
        pickSuggestionManually(0);
    }

    public void pickSuggestionManually(int index) {
        if (mCompletionOn && mCompletions != null && index >= 0
                && index < mCompletions.length) {
            CompletionInfo ci = mCompletions[index];
            getCurrentInputConnection().commitCompletion(ci);
            if (mCandidateViewContainer != null) {
                mCandidateViewContainer.clear();
            }
            updateShiftKeyState(getCurrentInputEditorInfo());
        } else if (mComposing.length() > 0) {
            // If we were generating candidate suggestions for the current
            // text, we would commit one of them here.  But for this sample,
            // we will just commit the current text.
            commitTyped(getCurrentInputConnection());
        }
    }

    public void swipeRight() {
        if (mCompletionOn) {
            pickDefaultCandidate();
        }
    }

    public void swipeLeft() {
        handleBackspace();
    }

    public void swipeDown() {
        handleClose();
    }

    public void swipeUp() {
    }

    public void onPress(int primaryCode) {
    }

    public void onRelease(int primaryCode) {
    }

    public static void LOGD(String log) {
        if (DEBUG) {
            Log.d("[SoftKeyboard]", log);
        }
    }

}
