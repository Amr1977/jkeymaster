package com.tulskiy.keymaster.windows;

import com.tulskiy.keymaster.common.Provider;
import org.bridj.Pointer;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.tulskiy.keymaster.windows.User32.*;
import static java.awt.event.KeyEvent.*;

/**
 * Author: Denis Tulskiy
 * Date: 6/12/11
 */
public class WindowsProvider implements Provider {
    private static final Map<Integer, Integer> codeExceptions = new HashMap<Integer, Integer>() {{
        put(VK_INSERT, 0x2D);
        put(VK_DELETE, 0x2E);
        put(VK_ENTER, 0x0D);
        put(VK_COMMA, 0xBC);
        put(VK_PERIOD, 0xBE);
        put(VK_PLUS, 0xBB);
        put(VK_MINUS, 0xBD);
        put(VK_SLASH, 0xBF);
        put(VK_SEMICOLON, 0xBA);
    }};

    private static volatile int idSeq = 0;

    private Map<Integer, ActionListener> idToListener = Collections.synchronizedMap(new HashMap<Integer, ActionListener>());
    private Map<Integer, KeyStroke> idToKeyStroke = Collections.synchronizedMap(new HashMap<Integer, KeyStroke>());
    private final Map<KeyStroke, ActionListener> toRegister = new HashMap<KeyStroke, ActionListener>();
    private boolean listen;
    private Boolean reset = false;
    private Thread thread;

    public void init() {
        Runnable runnable = new Runnable() {
            public void run() {
                Pointer<MSG> msgPointer = Pointer.allocate(MSG.class);
                listen = true;
                while (listen) {
                    synchronized (reset) {
                        if (reset) {
                            for (Integer id : idToListener.keySet()) {
                                UnregisterHotKey(null, id);
                            }

                            idToListener.clear();
                            idToKeyStroke.clear();
                            reset = false;
                        }
                    }

                    synchronized (toRegister) {
                        for (Map.Entry<KeyStroke, ActionListener> entry : toRegister.entrySet()) {
                            int id = idSeq++;
                            KeyStroke keyCode = entry.getKey();
                            ActionListener listener = entry.getValue();
                            int code = keyCode.getKeyCode();
                            if (codeExceptions.containsKey(code)) {
                                code = codeExceptions.get(code);
                            }
                            if (RegisterHotKey(null, id, getModifiers(keyCode), code)) {
                                logger.info("Registering hotkey: " + keyCode);
                                idToListener.put(id, listener);
                                idToKeyStroke.put(id, keyCode);
                            } else {
                                logger.warning("Could not register hotkey: " + keyCode);
                            }
                        }
                        toRegister.clear();
                    }

                    while (PeekMessage(msgPointer, null, 0, 0, PM_REMOVE) != 0) {
                        MSG msg = msgPointer.get();
                        System.out.println(msg.wParam());
                        if (msg.message() == WM_HOTKEY) {
                            int id = msg.wParam();
                            ActionListener listener = idToListener.get(id);

                            if (listener != null) {
                                listener.actionPerformed(new ActionEvent(idToKeyStroke.get(id), 0, ""));
                            }
                        }
                    }
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        thread = new Thread(runnable);
        thread.start();
    }

    public void registerMediaKeyListener(ActionListener listener) {
        registerMediaKeys();
    }

    private void registerMediaKeys() {
//        if (RegisterHotKey(null, id, 0, VK_MEDIA_NEXT_TRACK)) {
//            mediaIds.put(MediaKey.MEDIA_NEXT_TRACK, id);
//        }
//        id = idSeq++;
//        if (RegisterHotKey(null, id, 0, VK_MEDIA_PREV_TRACK)) {
//            mediaIds.put(MediaKey.MEDIA_PREV_TRACK, id);
//        }
//        id = idSeq++;
//        if (RegisterHotKey(null, id, 0, VK_MEDIA_PLAY_PAUSE)) {
//            mediaIds.put(MediaKey.MEDIA_PLAY_PAUSE, id);
//        }
//        id = idSeq++;
//        if (RegisterHotKey(null, id, 0, VK_MEDIA_STOP)) {
//            mediaIds.put(MediaKey.MEDIA_STOP, id);
//        }
    }

    public void register(KeyStroke keyCode, ActionListener listener) {
        synchronized (toRegister) {
            toRegister.put(keyCode, listener);
        }
    }

    public void reset() {
        reset = true;
    }

    public void stop() {
        listen = false;
        if (thread != null) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public int getModifiers(KeyStroke keyCode) {
        int modifiers = 0;
        if ((keyCode.getModifiers() & InputEvent.SHIFT_DOWN_MASK) != 0) {
            modifiers |= MOD_SHIFT;
        }
        if ((keyCode.getModifiers() & InputEvent.CTRL_DOWN_MASK) != 0) {
            modifiers |= MOD_CONTROL;
        }
        if ((keyCode.getModifiers() & InputEvent.META_DOWN_MASK) != 0) {
            modifiers |= MOD_WIN;
        }
        if ((keyCode.getModifiers() & InputEvent.ALT_DOWN_MASK) != 0) {
            modifiers |= MOD_ALT;
        }
        return modifiers;
    }
}