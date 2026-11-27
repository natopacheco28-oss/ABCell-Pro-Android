package com.abcell.movil;
import org.junit.Test;
import static org.junit.Assert.*;
public class MoneyTest {
    @Test public void parsesWithoutFloatRounding(){assertEquals(10,Money.cents("0.10"));assertEquals(12345,Money.cents("123,45"));assertEquals(0,Money.cents(""));}
    @Test public void calculatesExactBalance(){assertEquals(20,Money.balance(30,10));assertEquals(30,Money.pay(30,10,20));}
    @Test public void formatsEditableCents(){assertEquals("0.01",Money.edit(1));assertEquals("120.50",Money.edit(12050));}
    @Test public void rejectsInvalidAmounts(){for(String s:new String[]{"-1","1.001","NaN","Infinity","1,000.50","1e3","1000000000","0x10"})assertThrows(IllegalArgumentException.class,()->Money.cents(s));}
    @Test public void rejectsOverpayments(){assertThrows(IllegalArgumentException.class,()->Money.pay(100,0,101));assertThrows(IllegalArgumentException.class,()->Money.pay(100,0,0));assertThrows(IllegalArgumentException.class,()->Money.balance(10,11));}
}
