package howto;

import java.util.Arrays;
import java.util.List;

public class Tiikr {

    public static void main(String[] args) {
        //user , data, baejung
        List<Integer> user = Arrays.asList(1,2,3);
        List<Integer> data = Arrays.asList(1,2,3,4);
        int baejung = 2;
        test(user, data, baejung);
    }



    static void test(List<Integer>  user, List<Integer>  data, int baejung) {
        int total = 8;
        int test = 6;
    }


}


//for (int j=0; j<user.size(); j=j+2) {
//        if (j == user.size()-1) {
//        System.out.println(data.get(i) + "->" + user.get(j)+", "+ user.get(0));
//        } else {
//        System.out.println(data.get(i) + "->" + user.get(j)+", "+ user.get(j+1));
//        }
//        }