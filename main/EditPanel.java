package main;

import java.util.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

//Contains everything needed to create or modify an entry
class EditPanel extends JPanel {

    //UI elements that we need later access to
    private JTextField quantityField;
    private JComboBox<Currency> currBox;
    private JTextField tickerField;
    private JCheckBox priceUpdateBox;
    private JTextField priceField;
    private JCheckBox divUpdateBox;
    private JTextField divField;
    private JPanel tagsPane;
    private ArrayList<JComboBox<String>> boxes;

    //Create panel and prepopulate fields
    public EditPanel(Entry entry) {
        setLayout(new GridBagLayout());

        //Name
        JLabel tickerLabel = new JLabel("Name/Ticker");
        GridBagConstraints tickerLabelC = Main.createGridBagConstraints(0, 1, 0, 1);
        add(tickerLabel, tickerLabelC);

        tickerField = new JTextField(entry != null ? entry.getTicker() : "");
        GridBagConstraints tickerFieldC = Main.createGridBagConstraints(1, 1, 0, 1);
        tickerFieldC.weightx = 0.5;
        tickerFieldC.fill = GridBagConstraints.HORIZONTAL;
        tickerFieldC.anchor = GridBagConstraints.EAST;
        add(tickerField, tickerFieldC);

        //Currency
        JLabel currencyLabel = new JLabel("Currency");
        GridBagConstraints currencyLabelC = Main.createGridBagConstraints(0, 1, 1, 1);
        add(currencyLabel, currencyLabelC);
        
        currBox = new JComboBox<Currency>(Currency.values());
        if (entry != null)
            currBox.setSelectedItem(entry.getCurrency());
        GridBagConstraints currBoxC = Main.createGridBagConstraints(1, 1, 1, 1);
        currBoxC.anchor = GridBagConstraints.WEST;
        add(currBox, currBoxC);

        //Quantity
        JLabel quantityLabel = new JLabel("Quantity");
        GridBagConstraints quantityLabelC = Main.createGridBagConstraints(0, 1, 2, 1);
        add(quantityLabel, quantityLabelC);

        quantityField = new JTextField(entry != null ? entry.getQuantity() : "");
        GridBagConstraints quantityFieldC = Main.createGridBagConstraints(1, 1, 2, 1);
        quantityFieldC.weightx = 0.5;
        quantityFieldC.fill = GridBagConstraints.HORIZONTAL;
        quantityFieldC.anchor = GridBagConstraints.EAST;
        add(quantityField, quantityFieldC);

        //Price
        priceField = new JTextField(entry != null ? entry.getPrice() : "");
        priceField.setEditable(entry != null ? !entry.getUpdatePrice() : false);
        GridBagConstraints priceFieldC = Main.createGridBagConstraints(1, 1, 3, 1);
        priceFieldC.weightx = 0.5;
        priceFieldC.fill = GridBagConstraints.HORIZONTAL;
        priceFieldC.anchor = GridBagConstraints.EAST;
        add(priceField, priceFieldC);

        priceUpdateBox = new JCheckBox("Automatic price, else specify:", entry != null ? entry.getUpdatePrice() : true);
        priceUpdateBox.addItemListener(e -> {
            if(e.getStateChange() == 1){
                priceField.setText("");
                priceField.setEditable(false);
            }
            else{
                priceField.setEditable(true);
            }
        });
        GridBagConstraints priceUpdateBoxC = Main.createGridBagConstraints(0, 1, 3, 1);
        add(priceUpdateBox, priceUpdateBoxC);

        //Yield
        divField = new JTextField(entry != null ? entry.getYield() : "");
        divField.setEditable(entry != null ? !entry.getUpdateDiv() : false);
        GridBagConstraints divFieldC = Main.createGridBagConstraints(1, 1, 4, 1);
        divFieldC.weightx = 0.5;
        divFieldC.fill = GridBagConstraints.HORIZONTAL;
        divFieldC.anchor = GridBagConstraints.EAST;
        add(divField, divFieldC);

        divUpdateBox = new JCheckBox("Automatic yield, else specify (%):", entry != null ? entry.getUpdateDiv() : true);
        divUpdateBox.addItemListener(e -> {
            if(e.getStateChange() == 1){
                divField.setText("");
                divField.setEditable(false);
            }
            else{
                divField.setEditable(true);
            }
        });
        GridBagConstraints divUpdateBoxC = Main.createGridBagConstraints(0, 1, 4, 1);
        add(divUpdateBox, divUpdateBoxC);

        //Top row
        tagsPane = new JPanel(new GridLayout(0, 2));
        tagsPane.add(new JLabel("Tag"));
        tagsPane.add(new JLabel("Value"));
        
        //Create row
        boxes = new ArrayList<>();
        JButton addItemButton = new JButton("+");
        addItemButton.addActionListener(e -> {
            tagsPane.remove(addItemButton);
            JComboBox<String> newTagField = new JComboBox<String>(Db.getTags());
            newTagField.setEditable(true);

            tagsPane.add(newTagField);
            boxes.add(newTagField);

            JComboBox<String> newTagValueField = new JComboBox<String>();
            newTagValueField.setEditable(true);
            Component component = newTagValueField.getEditor().getEditorComponent();
            if (component instanceof JTextField){
                JTextField comboField = (JTextField) component;
                comboField.addFocusListener(new FocusListener(){
                    @Override
                    public void focusGained(FocusEvent e){
                        Object tag = newTagField.getSelectedItem();
                        Object currentVal = newTagValueField.getSelectedItem();
                        //Prepopulate with valid entry
                        if(tag != null){
                            String[] validValues = Db.getValuesForTag(tag.toString());
                            if (validValues != null){
                                newTagValueField.removeAllItems();
                                for(String entry : validValues){
                                    newTagValueField.addItem(entry);
                                }
                                if(currentVal != null){
                                    newTagValueField.setSelectedItem(currentVal);
                                }
                            }
                        }
                    };

                    @Override
                    public void focusLost(FocusEvent e){};
                });
            }

            tagsPane.add(newTagValueField);
            boxes.add(newTagValueField);
            tagsPane.add(addItemButton);

            this.validate();
        });
        tagsPane.add(addItemButton);

        //Scroll box
        JScrollPane tagScrollPane = new JScrollPane(tagsPane);
        GridBagConstraints tagScrollPaneC = Main.createGridBagConstraints(0, 2, 5, 4);
        tagScrollPaneC.weighty = 0.5;
        tagScrollPaneC.weightx = 0.5;
        tagScrollPaneC.fill = GridBagConstraints.BOTH;
        add(tagScrollPane, tagScrollPaneC);

        //Initialize the boxes only if entry exists
        if (entry != null){
            int i = 0;
            Set<Map.Entry<String, String>> tags = entry.getIterable();
            for(Map.Entry<String, String> kv : tags){
                addItemButton.doClick();
                boxes.get(i).setSelectedItem(kv.getKey());
                boxes.get(i+1).setSelectedItem(kv.getValue());
                i += 2;
            }
        }
    }

    //Create panel without prepopulating
    public EditPanel() {
        this(null);
    }

    private boolean isValidNum(float num){
        return num > 0f && Float.isFinite(num);
    }

    //Tries to construct the entry according to the values in the panel, creating a popup displaying the result
    //The modify flag is used to determine the correct wording for this popup
    //Returns the constructed entry if successful, else null
    public Entry tryConstruct(boolean modify) {
        //Key entry properties
        String ticker = tickerField.getText();
        float quantity = -1f;
        boolean willUpdatePrice = priceUpdateBox.isSelected();
        boolean willUpdateDiv = divUpdateBox.isSelected();
        float price = -1f;
        float div = -1f;
        Entry toAdd = null;
        
        String msg = "";
        boolean resultPassed = true;

        //Valid ticker check
        if("".equals(ticker)){
            msg = "The ticker must be set.";
            resultPassed = false;
        }

        //Number checks use this string format for failure
        String msgStart = "Could not interpret the ";
        String msgMiddle = " field (";
        String msgEnd = ") as a valid number.";

        //Quantity check
        if(resultPassed){
            String quantityText = quantityField.getText();
            try{
                quantity = Float.parseFloat(quantityText);
            } catch(NumberFormatException unused){}
            if(!isValidNum(quantity)){
                msg = msgStart + "quantity" + msgMiddle + quantityText + msgEnd;
                resultPassed = false;
            }
        }
            
        //Manual price check
        if(resultPassed && !willUpdatePrice){
            String priceText = priceField.getText();
            try{
                price = Float.parseFloat(priceText);
            } catch(NumberFormatException unused){}
            if(!isValidNum(price)){
                msg = msgStart + "price" + msgMiddle + priceText + msgEnd;
                resultPassed = false;
            }
        }

        //Manual yield check
        if(resultPassed && !willUpdateDiv){
            String divText = divField.getText();
            try{
                div = Float.parseFloat(divText)/100f;
            } catch(NumberFormatException unused){}
            if(div < 0f || !Float.isFinite(div)){
                msg = msgStart + "yield" + msgMiddle + divText + msgEnd;
                resultPassed = false;
            }
        }
        
        //Updates come last to reduce traffic
        msgStart = "Could not find a ";
        msgEnd = " for the ticker " + ticker + ".";

        //Auto price check
        if(resultPassed && willUpdatePrice){
            price = PriceWorker.updatePrice(ticker);
            if(price < 0f){
                msg = msgStart + "price" + msgEnd;
                resultPassed = false;
            }
        }

        //Auto yield check
        if(resultPassed && willUpdateDiv){
            div = PriceWorker.updateDiv(ticker, price);
            if(div < 0f){
                msg = msgStart + "yield" + msgEnd;
                resultPassed = false;
            }
        }

        //All checks complete, create entry
        if(resultPassed){
            //Create the entry and clear the fields
            toAdd = new Entry(ticker, quantity, willUpdatePrice, price, willUpdateDiv, div, (Currency) currBox.getSelectedItem());
            
            for(int i = 0; i < boxes.size(); i += 2){
                Object tagO = boxes.get(i).getSelectedItem();
                if(tagO == null){
                    continue;
                }
                String tag = tagO.toString();

                Object valueO = boxes.get(i + 1).getSelectedItem();
                if(valueO == null){
                    continue;
                }
                String value = valueO.toString();
                if(!"".equals(tag) && !"".equals(value)){
                    toAdd.addValue(tag, value);
                }
            }

            
            if(!modify){//Reset fields; UI is preserved
                quantityField.setText("");
                tickerField.setText("");
                priceField.setText("");
                if(!willUpdatePrice){
                    priceUpdateBox.doClick();
                }
                if(!willUpdateDiv){
                    divUpdateBox.doClick();
                }
                for(Component i : tagsPane.getComponents()){
                    if(i instanceof JComboBox<?>){
                        tagsPane.remove((JComboBox<?>) i);
                    }
                }
                boxes.clear();

                msg = "The entry was created successfully.";
            }
            else{
                msg = "The entry was modified successfully.";
            }
            if(willUpdatePrice){
                msg += String.format("\nFound a price of %.2f.", price);
            }
            if(willUpdateDiv){
                msg += String.format("\nFound a yield of %.2f%%.", 100*div);
            }
        }

        JOptionPane.showMessageDialog(this, msg, (resultPassed ? "Operation successful" : "Operation failed"), (resultPassed ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE));

        return toAdd;
    }

}
