package org.cdm.logisim.uart;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SocketThread extends Thread {

    private static final int DEFAULT_PORT = 7241;

    private final int port;

    private ServerSocket server = null;
    private Socket socket = null;
    private InputStream in  = null;
    private OutputStream out = null;

    private final Queue<Integer> rxBuffer = new ConcurrentLinkedQueue<>();

    private Status status;
    private String errorString;

    public SocketThread(int port) {
        this.port = port;
    }

    public SocketThread() {
        this(DEFAULT_PORT);
    }

    public int read() {
        Integer read = rxBuffer.poll();

        return read == null ? -1 : read;
    }

    public void write(int data) throws IOException {
        out.write(data);
    }

    public boolean connected() {
        return status == Status.CONNECTED;
    }

    public Status getStatus() {
        return status;
    }

    public String getErrorString() {
        return errorString;
    }

    @Override
    public void run() {
        status = Status.DOWN;

        try {
            server = new ServerSocket(port);
        } catch (BindException e) {
            status = Status.BIND_ERROR;
            errorString = e.getMessage();
            return;
        } catch (IOException e) {
            handleIOException(e);
            return;
        }

        // System.out.println("Server started on port " + port);

        while (true) {
            if (status == Status.ERROR) {
                break;
            }

            try {
                status = Status.WAITING;

                // System.out.println("Waiting for a client ...");

                socket = server.accept();

                status = Status.CONNECTING;

                // System.out.println("Client accepted");

                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                /*out.write(0xff);
                out.write(0xfe);
                out.write(0x1);*/

                status = Status.CONNECTED;

                int read;

                while ((read = in.read()) != -1) {
                    rxBuffer.add(read);
                }

                status = Status.DISCONNECTING;

                in.close();
                out.close();
                socket.close();

            } catch (SocketException e) {
                status = Status.DISCONNECTING;

                try {
                    in.close();
                    out.close();
                    socket.close();
                } catch (IOException ex) {
                    handleIOException(ex);
                }

            } catch (IOException e) {
                handleIOException(e);
            }
        }
    }

    private void handleIOException(IOException e) {
        status = Status.ERROR;
        errorString = e.getMessage();
        System.err.println(e);
    }
}
