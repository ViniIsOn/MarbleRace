package com.viniison.marblerace;

/** Headless checks for the real selector, scythe, item belt and weapon effects. */
public final class CoreEquipmentSmokeTest {
    private static void check(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args){
        int[] seenTypes=new int[CoreEquipmentSystem.NAMES.length];
        int[] chosenTeams=new int[2];
        int total=0;
        for(int k=0;k<24;k++){
            long seed=15802515L + k*97561L;
            CoreEquipmentSystem a=new CoreEquipmentSystem(seed);
            CoreEquipmentSystem b=new CoreEquipmentSystem(seed);
            boolean moved=false,dropped=false;
            for(int f=0;f<120*60;f++){
                CoreEquipmentSystem.Delivery deliveryA=a.update(1f/60f);
                CoreEquipmentSystem.Delivery deliveryB=b.update(1f/60f);
                check(a.phase==b.phase&&Float.compare(a.scytheY,b.scytheY)==0,
                    "Scythe animation is not deterministic");
                if(a.phase==CoreEquipmentSystem.DESCENDING&&a.scytheY>700)dropped=true;
                if(f>100&&a.conveyor.size()>0&&a.conveyor.get(0).x!=55f)moved=true;
                if(deliveryA!=null||deliveryB!=null){
                    check(deliveryA!=null&&deliveryB!=null&&
                        deliveryA.side==deliveryB.side&&deliveryA.type==deliveryB.type,
                        "Delivery changed during an identical replay");
                    check(deliveryA.side>=0&&deliveryA.side<2&&
                        deliveryA.type>=0&&deliveryA.type<6,"Invalid equipment");
                    seenTypes[deliveryA.type]++;chosenTeams[deliveryA.side]++;total++;
                }
                check(a.conveyor.size()==CoreEquipmentSystem.ITEM_COUNT,
                    "The conveyor leaked or lost equipment capsules");
            }
            check(dropped&&moved&&a.deliveries>=4,
                "The picker/scythe or conveyor was only decorative");
        }
        int types=0;for(int t:seenTypes)if(t>0)types++;
        check(types==6,"Some equipment never becomes available: only "+types+" types");
        check(chosenTeams[0]>0&&chosenTeams[1]>0,
            "Selector marbles did not choose both sides across seeds");
        CoreBattleEngine game=new CoreBattleEngine(4931);
        int shots=0;
        for(int i=0;i<120*30&&!game.finished;i++){
            game.advance(1f/30f);
            if(game.equipment.deliveries>0)shots=game.equipment.deliveries;
        }
        check(shots>=4,"Weapons do not reach the battle engine");
        check(game.weaponHits[0]+game.weaponHits[1]+
              game.equipment.deliveriesByType[CoreEquipmentSystem.SHIELD]>0,
              "No equipment modified the actual battle state");
        System.out.println("PASS: "+total+" deterministic physical item deliveries, all six types and both teams; integrated battle weapons="+shots);
    }
}
