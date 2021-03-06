package com.koushikdutta.async;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;

import junit.framework.Assert;

import com.koushikdutta.async.callback.DataCallback;

public class PushParser {
    private LinkedList<Object> mWaiting = new LinkedList<Object>();

    static class BufferWaiter {
        int length;
    }
    
    static class UntilWaiter {
        byte value;
        DataCallback callback;
    }
    
    int mNeeded = 0;
    public PushParser readInt() {
        mNeeded += 4;
        mWaiting.add(int.class);
        return this;
    }

    public PushParser readByte() {
        mNeeded += 1;
        mWaiting.add(byte.class);
        return this;
    }
    
    public PushParser readShort() {
        mNeeded += 2;
        mWaiting.add(short.class);
        return this;
    }
    
    public PushParser readLong() {
        mNeeded += 8;
        mWaiting.add(long.class);
        return this;
    }
    
    public PushParser readBuffer(int length) {
        if (length != -1)
            mNeeded += length;
        BufferWaiter bw = new BufferWaiter();
        bw.length = length;
        mWaiting.add(bw);
        return this;
    }

    public PushParser readLenBuffer() {
        readInt();
        BufferWaiter bw = new BufferWaiter();
        bw.length = -1;
        mWaiting.add(bw);
        return this;
    }
    
    public PushParser until(byte b, DataCallback callback) {
        UntilWaiter waiter = new UntilWaiter();
        waiter.value = b;
        waiter.callback = callback;
        mWaiting.add(b);
        return this;
    }
    
    public PushParser noop() {
        mWaiting.add(Object.class);
        return this;
    }

    AsyncInputStream mReader;
    DataEmitter mEmitter;
    public PushParser(DataEmitter s) {
        mEmitter = s;
        mReader = new DataEmitterStream(s);
    }
    
    private ArrayList<Object> mArgs = new ArrayList<Object>();
    private TapCallback mCallback;
    
    Exception stack() {
        try {
            throw new Exception();
        }
        catch (Exception e) {
            return e;
        }
    }
    
    public void tap(TapCallback callback) {
        Assert.assertNull(mCallback);
        Assert.assertTrue(mWaiting.size() > 0);
        
        mCallback = callback;
        
        new DataCallback() {
            {
                onDataAvailable(mEmitter, null);
            }
            
            @Override
            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                try {
                    while (mWaiting.size() > 0) {
                        Object waiting = mWaiting.peek();
                        if (waiting == null)
                            break;
//                        System.out.println("Remaining: " + bb.remaining());
                        if (waiting == int.class) {
                            mArgs.add(bb.getInt());
                            mNeeded -= 4;
                        }
                        else if (waiting == short.class) {
                            mArgs.add(bb.getShort());
                            mNeeded -= 2;
                        }
                        else if (waiting == byte.class) {
                            mArgs.add(bb.get());
                            mNeeded -= 1;
                        }
                        else if (waiting == long.class) {
                            mArgs.add(bb.getLong());
                            mNeeded -= 8;
                        }
                        else if (waiting == Object.class) {
                            mArgs.add(null);
                        }
                        else if (waiting instanceof UntilWaiter) {
                            UntilWaiter uw = (UntilWaiter)waiting;
                            boolean found = false;
                            ByteBufferList cb = new ByteBufferList();
                            ByteBuffer lastBuffer = null;
                            do {
                                if (lastBuffer != bb.peek()) {
                                    lastBuffer.mark();
                                    if (lastBuffer != null) {
                                        lastBuffer.reset();
                                        cb.add(lastBuffer);
                                    }
                                    lastBuffer = bb.peek();
                                }
                            }
                            while (bb.remaining() > 0 && (found = (bb.get() != uw.value)));

                            int mark = lastBuffer.position();
                            lastBuffer.reset();
                            ByteBuffer add = ByteBuffer.wrap(lastBuffer.array(), lastBuffer.arrayOffset() + lastBuffer.position(), mark - lastBuffer.position());
                            cb.add(add);
                            lastBuffer.position(mark);
                            
                            if (!found) {
                                if (uw.callback != null)
                                    uw.callback.onDataAvailable(emitter, cb);
                                throw new Exception();
                            }
                        }
                        else if (waiting instanceof BufferWaiter) {
                            BufferWaiter bw = (BufferWaiter)waiting;
                            int length = bw.length;
                            if (length == -1) {
                                length = (Integer)mArgs.get(mArgs.size() - 1);
                                mArgs.remove(mArgs.size() - 1);
                                bw.length = length;
                                mNeeded += length;
                            }
                            if (bb.remaining() < length) {
//                                System.out.print("imminient feilure detected");
                                throw new Exception();
                            }
                            
//                            e.printStackTrace();
//                            System.out.println("Buffer length: " + length);
                            byte[] bytes = null;
                            if (length > 0) {
                                bytes = new byte[length];
                                bb.get(bytes);
                            }
                            mNeeded -= length;
                            mArgs.add(bytes);
                        }
                        else {
                            Assert.fail();
                        }
//                        System.out.println("Parsed: " + mArgs.get(0));
                        mWaiting.remove();
                    }
                }
                catch (Exception ex) {
                    Assert.assertTrue(mNeeded != 0);
//                    ex.printStackTrace();
                    mReader.read(mNeeded, this);
                    return;
                }
                
                try {
                    Object[] args = mArgs.toArray();
                    mArgs.clear();
                    TapCallback callback = mCallback;
                    mCallback = null;
                    Method method = callback.getTap();
                    method.invoke(callback, args);
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        };
    }
}
