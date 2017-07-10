
// https://github.com/foxdog-studios/peepers/blob/master/src/com/foxdogstudios/peepers/MJpegHttpStreamer.java

package com.nauto.camera;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

final class MjpegHttpStreamer {
    private static final String TAG = MjpegHttpStreamer.class.getName();
    private static final String BOUNDARY = "--gc0p4Jq0M2Yt08jU534c0p--";
    private static final String BOUNDARY_LINES = "\r\n" + BOUNDARY + "\r\n";
    private static final String HTTP_HEADER =
            "HTTP/1.0 200 OK\r\n"
                    + "Server: Peepers\r\n"
                    + "Connection: close\r\n"
                    + "Max-Age: 0\r\n"
                    + "Expires: 0\r\n"
                    + "Cache-Control: no-store, no-cache, must-revalidate, pre-check=0, "
                    + "post-check=0, max-age=0\r\n"
                    + "Pragma: no-cache\r\n"
                    + "Content-Type: multipart/x-mixed-replace; "
                    + "boundary=" + BOUNDARY + "\r\n"
                    + BOUNDARY_LINES;

    private final int _port;
    private boolean _newJpeg = false;
    private boolean _streamingBufferA = true;
    private final byte[] _bufferA;
    private final byte[] _bufferB;
    private int _lengthA = Integer.MIN_VALUE;
    private int _lengthB = Integer.MIN_VALUE;
    private long _timestampA = Long.MIN_VALUE;
    private long _timestampB = Long.MIN_VALUE;
    private final Object _bufferLock = new Object();
    private Thread _worker = null;
    private volatile boolean _running = false;

    MjpegHttpStreamer(final int port, final int bufferSize) {
        _port = port;
        _bufferA = new byte[bufferSize];
        _bufferB = new byte[bufferSize];
    }

    void start() {
        if (_running) {
            throw new IllegalStateException("MjpegHttpStreamer is already running");
        }
        _running = true;
        _worker = new Thread(new Runnable() {
            @Override
            public void run() {
                workerRun();
            }
        });
        _worker.start();
    }

    void stop() {
        if (!_running) {
            throw new IllegalStateException("MjpegHttpStreamer is already stopped");
        }

        _running = false;
        _worker.interrupt();
    }

    void streamJpeg(final byte[] jpeg, final int length, final long timestamp) {
        synchronized (_bufferLock) {
            final byte[] buffer;
            if (_streamingBufferA) {
                buffer = _bufferB;
                _lengthB = length;
                _timestampB = timestamp;
            } else {
                buffer = _bufferA;
                _lengthA = length;
                _timestampA = timestamp;
            }
            System.arraycopy(jpeg, 0 /* srcPos */, buffer, 0 /* dstPos */, length);
            _newJpeg = true;
            _bufferLock.notify();
        }
    }

    private void workerRun() {
        while (_running) {
            try {
                acceptAndStream();
            } catch (final IOException exceptionWhileStreaming) {
                System.err.println(exceptionWhileStreaming);
            }
        }
    }

    private void acceptAndStream() throws IOException {
        ServerSocket serverSocket = null;
        Socket socket = null;
        DataOutputStream stream = null;

        try {
            serverSocket = new ServerSocket(_port);
            serverSocket.setSoTimeout(1000);

            do {
                try {
                    socket = serverSocket.accept();
                } catch (final SocketTimeoutException e) {
                    if (!_running) {
                        return;
                    }
                }
            } while (socket == null);

            serverSocket.close();
            serverSocket = null;
            stream = new DataOutputStream(socket.getOutputStream());
            stream.writeBytes(HTTP_HEADER);
            stream.flush();

            while (_running) {
                final byte[] buffer;
                final int length;
                final long timestamp;

                synchronized (_bufferLock) {
                    while (!_newJpeg) {
                        try {
                            _bufferLock.wait();
                        } catch (final InterruptedException ex) {
                            ex.printStackTrace();
                            return;
                        }
                    }

                    _streamingBufferA = !_streamingBufferA;

                    if (_streamingBufferA) {
                        buffer = _bufferA;
                        length = _lengthA;
                        timestamp = _timestampA;
                    } else {
                        buffer = _bufferB;
                        length = _lengthB;
                        timestamp = _timestampB;
                    }

                    _newJpeg = false;
                }

                stream.writeBytes("Content-type: image/jpeg\r\n"
                        + "Content-Length: " + length + "\r\n"
                        + "X-Timestamp:" + timestamp + "\r\n"
                        + "\r\n"
                );
                stream.write(buffer, 0 /* offset */, length);
                stream.writeBytes(BOUNDARY_LINES);
                stream.flush();
            }
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }
}