/*
 * Copyright (C) 2008-2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yiyun.softkeyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.inputmethod.InputMethodSubtype;

import java.util.List;

import com.yiyun.softkeyboard.CustomKeyboard.Key;

public class LatinKeyboardView extends CustomKeyboardView {

    private Context mContext;

    public LatinKeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    public LatinKeyboardView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
    }

    @Override
    protected boolean onLongPress(Key key) {
//        if (key.codes[0] == KeyCode.KEYCODE_CANCEL) {
//            getOnKeyboardActionListener().onKey(KeyCode.KEYCODE_OPTIONS, null, "");
//            return true;
//        } else {
//            return super.onLongPress(key);
//        }
        return super.onLongPress(key);
    }

    void setSubtypeOnSpaceKey(final InputMethodSubtype subtype) {
        final LatinKeyboard keyboard = (LatinKeyboard)getKeyboard();
        keyboard.setSpaceIcon(getResources().getDrawable(subtype.getIconResId()));
        invalidateAllKeys();
    }

//    @Override
//    public void onDraw(Canvas canvas) {
//        super.onDraw(canvas);
//
//        List<Key> keys = getKeyboard().getKeys();
//        for (Key key : keys) {
//            if (key.label != null) {
//                ColorDrawable dr = new ColorDrawable(mContext.getResources().getColor(R.color.colorPrimary));
//                //Drawable dr = (Drawable) mContext.getResources().getColor(R.color.colorPrimary);//.getDrawable(R.mipmap.sym_keyboard_space);
//                dr.setBounds(key.x, key.y, key.x + key.width, key.y + key.height);
//                dr.draw(canvas);
//            } else {
//                ColorDrawable dr = new ColorDrawable(mContext.getResources().getColor(R.color.colorAccent));
//                //Drawable dr = (Drawable) mContext.getResources().getDrawable(R.mipmap.sym_keyboard_search);
//                dr.setBounds(key.x, key.y, key.x + key.width, key.y + key.height);
//                dr.draw(canvas);
//            }
//        }
//    }
}
