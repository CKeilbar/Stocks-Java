package main;

import java.util.*;
import java.awt.*;
import javax.swing.JComponent;
import javax.swing.JFrame;
import java.lang.Math.*;

//This a resizable component that displays a pie graph of the input map
class Pie extends JComponent{
    private Map<String, Float> localMap;
    private ArrayList<Color> colourList;
    private int numItems;
    private float total;

    //Takes in a map of tickers and their values
    //The tickers aren't used but are valuable for the caller
    Pie(Map<String, Float> graphable){
        localMap = graphable;
        total = 0f;

        numItems = graphable.size();

        for(Float i : localMap.values()){
            total += i;
        }

        //Create the list of colours by stepping through the hue
        colourList = new ArrayList<>(numItems);
        float step = 1.0f / numItems;
        float[] baseHSB = Color.RGBtoHSB(55, 148, 187, null);

        for(int i = 0; i < numItems; i++){
            float newHue = (baseHSB[0] + step * ((float) i)) % 1f;
            colourList.add(new Color(Color.HSBtoRGB(newHue, baseHSB[1], baseHSB[2])));
        }
        //Needed to prevent it being a colour wheel
        Collections.shuffle(colourList);
    }

    @Override
    public void paint(Graphics g) {
        drawPie((Graphics2D) g, getBounds());
    }

    //Used to match the legend colours with the pie colours
    public ArrayList<Color> getColours(){
        return colourList;
    };

    public float getTotal(){
        return total;
    };

    void drawPie(Graphics2D g, Rectangle area){
        int startAngle = 0;
        int j = 0;
        int size; //The graph is a square
        float runningTotal = 0f;

        //Where to draw the border line from
        int x = area.x;
        int y = area.y;
        int width = area.width;
        int height = area.height-20; //Need the -20 to ensure the pie doesn't get squished

        //For centering
        if(width > height){
            size = height;
            x += (area.width - height)/2;
        }
        else{
            size = width;
            y += (height - width)/2;
        }
        int xOrigin = x+size/2;
        int yOrigin = y+size/2;


        //For a nice border
        g.setStroke(new BasicStroke(2));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        //Draw the graph sector by sector
        for(Float i : localMap.values()){
            runningTotal += i;
            int stopAngle = Math.round(runningTotal*360/total);
            int arcAngle = stopAngle-startAngle;

            //Fill arc with solid colour
            g.setColor(colourList.get(j));
            g.fillArc(x, y, size, size, startAngle, arcAngle);

            //Border
            g.setColor(Color.BLACK);
            if(j != 0){
                g.drawLine(xOrigin, yOrigin, xOrigin+(int)(0.5*size*Math.cos(Math.toRadians(startAngle))), yOrigin-(int)(0.5*size*Math.sin(Math.toRadians(startAngle))));
            }
            startAngle = stopAngle;
            j++;
        }

        //Finalize the outline
        g.drawOval(x, y, size, size);
        if (j != 1)
            g.drawLine(xOrigin, yOrigin, xOrigin+size/2, yOrigin); //Redraw the first line, it gets partially overlapped otherwise
   }
}
