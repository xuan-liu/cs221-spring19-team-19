package edu.uci.ics.cs221.index.inverted;

import java.util.List;

/**
 * Implement this compressor with Delta Encoding and Variable-Length Encoding.
 * See Project 3 description for details.
 */

public class DeltaVarLenCompressor implements Compressor {
    
    /**
     * Encodes a list of integers to a byte array.
     */

    @Override
    public byte[] encode(List<Integer> integers) {
        List<Byte> vec = new ArrayList<>();
        int offset = 0;
        for (int num : integers) {
            
            // calculating the offset of the number from the previous one
            num -= offset;
            
            // a flag to check if the number needs more than one byte
            boolean flag = false;
            List<Byte> temp = new ArrayList<>();
            int count = 1;
            
            // binary representation of num
            while (num > 0) {
                if (count % 8 == 0) {
                    if (flag) {
                        temp.add((byte) 1);
                    }
                    else {
                        temp.add((byte) 0);
                    }
                    flag = true;
                    count++;
                    continue;
                }
                int c = num % 2;
                temp.add((byte) c);
                num /= 2;
                count++;
            }
            
            // filling the left bytes
            int left = temp.size() % 8;
            if (left != 0) {
                for (int j = 0; j < 8 - left; j++) {
                    if (j == 7 - left && flag) {
                        temp.add((byte) 1);
                    }
                    else {
                        temp.add((byte) 0);
                    }
                }
            }
            
            // adding the results
            Collections.reverse(temp);
            vec.addAll(temp);
            
            // updating the offset
            offset += num;
        }
        byte[] coded = new byte[vec.size()];
        for (int i = 0; i < vec.size(); i++) {
            coded[i] = vec.get(i);
        }
        return coded;
    }

    @Override
    public List<Integer> decode(byte[] bytes, int start, int length) {
        throw new UnsupportedOperationException();
    }
}
