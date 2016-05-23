
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Random;

/**
 * Author: Andrew Jarombek
 * Date: 3/7/2016 - 3/29/2016
 * The Server Side of the UFT data transfer.
 */
public class UFTServer {

    public static void main(String[] args) {

        // Get input from the command line
        String filename = args[0];
        File file = new File(filename);
        double corruptionRate = Double.parseDouble(args[1]);
        double lossRate = Double.parseDouble(args[2]);

        // Server Stats
        int totalPackets = 0;
        int dupPackets = 0;
        int totalAcks = 0;
        int dupAcks = 0;
        boolean start = false;
        double startTime = 0.0;
        double endTime = 0.0;

        // Make sure the corruption rate is valid
        if (corruptionRate < 0 || corruptionRate > 1) {
            System.out.println("ERROR: Invalid Corruption Rate");
            System.exit(0);
        }

        // Make sure the loss rate is valid
        if (lossRate < 0 || lossRate > 1) {
            System.out.println("ERROR: Invalid Loss Rate");
            System.exit(0);
        }

        // create a UDP socket to listen to data and to send data
        DatagramSocket serverSocket = null;
        try {
            serverSocket = new DatagramSocket(22600);
        } catch (SocketException e) {
            System.out.println("ERROR: Unable to Create UDP Socket.");
            System.exit(0);
        }

        // Create a FileOutputStream to read the data and write it to a file
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            System.out.println("ERROR: File Creation Failed.");
            System.exit(0);
        }

        boolean transferComplete = false;
        String lastSequenceNumber = "00";

        // wait for connections
        while (true) {

            // Loss and corruption simulation
            boolean corrupt = Simulate.isCorrupt(corruptionRate);
            boolean lost = Simulate.isLost(lossRate);

            // create a byte array to store the client message
            byte[] buffer = new byte[1003];

            // create an empty datagram packet to receive data
            DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);

            // receive data from a client
            // this blocks until data is received
            try {
                serverSocket.receive(receivePacket);
                totalPackets++;
                // Get the time that the transmitting began
                if (!start) {
                    startTime = System.currentTimeMillis();
                    start = true;
                }
            } catch (IOException e) {
                // Check if the transfer is complete (1 second timeout)
                // If complete, display statistics
                if (transferComplete) {
                    endTime = System.currentTimeMillis() - 1000.0;
                    System.out.println("\n+---------------STATS---------------+");
                    System.out.println("Elapsed Time: " + (endTime - startTime) + "ms");
                    System.out.println("Total Packets Received: " + totalPackets);
                    System.out.println("Duplicate Packets Received: " + dupPackets);
                    System.out.println("Total ACK Sent: " + totalAcks);
                    System.out.println("Duplicate ACK Sent: " + dupAcks + "\n");
                    System.exit(0);
                } else {
                    // Otherwise display error and exit program
                    System.out.println("ERROR: Packet not Received");
                    System.exit(0);
                }
            }

            // retrieve the actual number of bytes received
            byte[] clientBytes = Arrays.copyOf(receivePacket.getData(), receivePacket.getLength());

            // Create acknowledgement byte array
            byte[] seqNum = new byte[2];
            seqNum[0] = clientBytes[0];
            seqNum[1] = clientBytes[1];
            String sequenceNumber = String.valueOf(clientBytes[0]) + clientBytes[1];

            // Display received messages
            if (corrupt) {
                // If this packet is corrupted
                System.out.println("Received a Corrupt Packet");
                // if corrupted, use the previous sequence number
                sequenceNumber = lastSequenceNumber;
                seqNum[0] = (byte) Integer.parseInt(lastSequenceNumber.substring(0, 1));
                seqNum[1] = (byte) Integer.parseInt(lastSequenceNumber.substring(1, 2));
                dupAcks++;
            } else if (sequenceNumber.equals(lastSequenceNumber)) {
                // If the received packet is a duplicate
                System.out.println("Received Packet #" + sequenceNumber + " (Duplicate)");
                dupPackets++;
                // DID NOT ADD DUPLICATE ACK TO STATS IN THIS SCENARIO (ONLY CORRUPTION)
            } else {
                // If the packet was received successfully and was not a duplicate
                System.out.println("Received Packet #" + sequenceNumber);

                try {
                    output.write(clientBytes, 3, receivePacket.getLength()-3);
                } catch (IOException e) {
                    System.out.println("ERROR: Unable to Write File.");
                    System.exit(0);
                }
            }

            // Set the lastSequenceNumber for next received packet
            lastSequenceNumber = sequenceNumber;

            // Check if we are on the last packet
            transferComplete = (clientBytes[2] == 1);

            // If lost, do not send acknowledgement
            if (lost) {
                System.out.println("Lost ACK #" + sequenceNumber);
            } else {
                totalAcks++;
                // create a server packet to send
                DatagramPacket serverPacket =
                        new DatagramPacket(seqNum, seqNum.length,
                                receivePacket.getAddress(), receivePacket.getPort());

                // send the server packet
                try {
                    serverSocket.send(serverPacket);
                    // Once the file transfer has begun, set a timeout (1 Second)
                    serverSocket.setSoTimeout(1000);
                } catch (IOException e) {
                    System.out.println("ERROR: Unable to Send Acknowledgement.");
                    System.exit(0);
                }
                System.out.println("Sent ACK #" + sequenceNumber);
            }
        }
    }
}
