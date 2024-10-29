package org.telegram.messenger;

import android.net.Uri;

import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Util;

import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.http.NanoHTTPD;
import org.nanohttpd.protocols.http.response.Response;
import org.nanohttpd.protocols.http.response.Status;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CastMediaServer extends NanoHTTPD {
    private Map<String, LoadTask> operations = new HashMap<>();

    public CastMediaServer(int port) {
        super(port);
    }

    // TODO: HLS
    public Uri serve(String myIP, Uri uri) {
        operations.clear();

        String id = UUID.randomUUID().toString();

        LoadTask operation;
        try {
            operation = new LoadTask(uri);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        operations.put(id, operation);

        return Uri.parse("http://" + myIP + ":" + getListeningPort() + "/" + id);
    }

    @Override
    public Response handle(IHTTPSession session) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("Handle " + session.getUri() + ", " + session.getHeaders());
        }

        LoadTask task = operations.get(session.getUri().substring(1));
        if (task != null) {
            try {
                String range = session.getHeaders().get("range").substring(6);
                String[] spl = range.split("-");
                long startRange = Long.parseLong(spl[0]);
                long endRange = spl.length > 1 && !spl[1].isEmpty() ? Long.parseLong(spl[1]) : task.getSize();

                if (task.isFile()) {
                    File f = new File(task.uri.getPath());
                    if (endRange == task.getSize()) {
                        InputStream in = new FileInputStream(f);
                        Response r = Response.newChunkedResponse(Status.OK, task.getMimeType(), in);
                        r.addHeader("Content-Length", String.valueOf(task.getSize()));
                        r.addHeader("Accept-Ranges", "bytes");
                        return r;
                    } else {
                        RandomAccessFile file = new RandomAccessFile(task.uri.getPath(), "r");
                        file.seek(startRange);

                        Response r = Response.newChunkedResponse(Status.OK, task.getMimeType(), new LoadInputStream((int) (endRange - startRange)) {
                            @Override
                            protected int onRead(byte[] buffer, int offset, int len) throws IOException {
                                return file.read(buffer, offset, len);
                            }

                            @Override
                            public void close() throws IOException {
                                super.close();
                            }
                        });
                        r.addHeader("Content-Length", String.valueOf(endRange - startRange));
                        r.addHeader("Accept-Ranges", "bytes");
                        r.addHeader("Content-Range", "bytes " + startRange + "-" + endRange + "/" + task.getSize());
                        return r;
                    }
                } else {
                    FileStreamLoadOperation operation = new FileStreamLoadOperation();
                    DataSpec spec = new DataSpec(task.uri, startRange, endRange - startRange);
                    operation.open(spec);

                    Response r = Response.newChunkedResponse(Status.OK, task.getMimeType(), new LoadInputStream((int) (endRange - startRange)) {
                        @Override
                        protected int onRead(byte[] buffer, int offset, int len) throws IOException {
                            return operation.read(buffer, offset, len);
                        }

                        @Override
                        public void close() throws IOException {
                            super.close();
                            operation.close();
                        }
                    });
                    r.addHeader("Content-Length", String.valueOf(endRange - startRange));
                    r.addHeader("Accept-Ranges", "bytes");
                    r.addHeader("Content-Range", "bytes " + startRange + "-" + endRange + "/" + task.getSize());

                    return r;
                }
            } catch (IOException e) {
                FileLog.e(e);
            }
        }

        return Response.newFixedLengthResponse("Nope :)");
    }

    @Override
    public void stop() {
        super.stop();
        operations.clear();
    }

    private final static class LoadTask {
        private final Uri uri;

        private LoadTask(Uri uri) throws FileNotFoundException {
            this.uri = uri;
        }

        private boolean isFile() {
            return Util.isLocalFileUri(uri);
        }

        private String getMimeType() {
            return uri.getQueryParameter("mime");
        }

        private long getSize() throws IOException {
            return Long.parseLong(uri.getQueryParameter("size"));
        }
    }

    private abstract static class LoadInputStream extends InputStream {
        private int bytesRead;
        private int bytesAvailable;
        private int bytesRemaining;
        private byte[] buffer = new byte[10240];

        LoadInputStream(int total) {
            bytesRemaining = total;
        }

        protected abstract int onRead(byte[] buffer, int offset, int len) throws IOException;

        @Override
        public int read() throws IOException {
            if (bytesAvailable <= 0) {
                bytesAvailable = onRead(buffer, 0, Math.min(buffer.length, bytesRemaining));
                bytesRemaining -= bytesAvailable;
                bytesRead = 0;
            }
            if (bytesAvailable <= 0) return -1;
            int b = bytesRead++;
            bytesAvailable--;
            return buffer[b] & 0xFF;
        }

        @Override
        public int available() {
            return bytesRemaining;
        }
    }
}
