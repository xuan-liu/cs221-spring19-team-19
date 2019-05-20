package edu.uci.ics.cs221.index.inverted;

import java.util.List;

/**
 * Implement this compressor with Delta Encoding and Variable-Length Encoding.
 * See Project 3 description for details.
 */

public class DeltaVarLenCompressor implements Compressor {

    /**
    * Encoding each integer in the list.
    * @param integers the list of input integers.
    * @return the encoded byte array
    */
    
    @Override
    public byte[] encode(List<Integer> integers) {
        List<Byte> vec = new ArrayList<>();
        for (int num : integers) {
            boolean flag = false;
            List<Byte> temp = new ArrayList<>();
            int count = 1;
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
            Collections.reverse(temp);
            vec.addAll(temp);
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
