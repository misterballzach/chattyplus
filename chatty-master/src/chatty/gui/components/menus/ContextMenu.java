
package chatty.gui.components.menus;

import chatty.Helper;
import chatty.util.StringUtil;
import chatty.util.commands.CustomCommand;
import chatty.util.commands.Parameters;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;

/**
 * A Popup Menu with convenience methods to add items as well as items in
 * submenus.
 * 
 * @author tduva
 */
public abstract class ContextMenu extends JPopupMenu implements ActionListener {
    
    private final ActionListener listener = new ActionListener() {

        @Override
        public void actionPerformed(ActionEvent e) {
            ContextMenu.this.actionPerformed(getCommandActionEvent(e));
        }
    };
    
    private final Map<String, JMenu> subMenus = new HashMap<>();
    private final Set<ContextMenuListener> listeners = new HashSet<>();
    private final Map<String, CustomCommand> commands = new HashMap<>();
    private final Map<String, ButtonGroup> buttonGroups = new HashMap<>();
    private final Map<String, JMenuItem> items = new HashMap<>();
    
    private JMenuItem makeItem(String action, String text, ImageIcon icon) {
        JMenuItem item = new JMenuItem(text);
        item.setActionCommand(action);
        item.addActionListener(listener);
        if (icon != null) {
            item.setIcon(icon);
        }
        return item;
    }
    
    private JMenuItem makeCheckboxItem(String action, String text, boolean selected) {
        JMenuItem item = new JCheckBoxMenuItem(text, selected);
        item.setActionCommand(action);
        item.addActionListener(listener);
        return item;
    }
    
    private JMenuItem makeRadioItem(String action, String text, String group) {
        JMenuItem item = new JRadioButtonMenuItem(text);
        item.setActionCommand(action);
        item.addActionListener(listener);
        if (!buttonGroups.containsKey(group)) {
            buttonGroups.put(group, new ButtonGroup());
        }
        buttonGroups.get(group).add(item);
        return item;
    }
    
    /**
     * Adds an entry to the submenu with the name {@code parent}, after any
     * previously added items in that submenu.
     * 
     * @param action The action of the menu item, which will be send to the
     * listener
     * @param text The label of the menu item
     * @param pos The position in the menu, at the time of adding
     * @param parent The name of the submenu, can be {@code null} in which case
     * it isn't added to a submenu
     * @param icon The icon to display for the menu item
     * @return 
     * @see addItem(String, String, ImageIcon)
     */
    protected JMenuItem addItem(String action, String text, int pos, String parent, ImageIcon icon) {
        if (parent != null) {
            JMenuItem item = makeItem(action, text, icon);
            if (pos > -1) {
                getSubmenu(parent).insert(item, pos);
            } else {
                getSubmenu(parent).add(item);
            }
            items.put(action, item);
            return item;
        } else {
            JMenuItem item = makeItem(action, text, icon);
            if (pos > -1) {
                insert(item, pos);
            } else {
                add(item);
            }
            items.put(action, item);
            return item;
        }
    }
    
    public JMenuItem addItem(String action, String text) {
        return addItem(action, text, -1, null, null);
    }

    public JMenuItem addItem(String action, String text, ImageIcon icon) {
        return addItem(action, text, -1, null, icon);
    }
    
    public JMenuItem addItem(String action, String text, String parent) {
        return addItem(action, text, -1, parent, null);
    }
    
    public JMenuItem addItem(String action, String text, String parent, ImageIcon icon) {
        return addItem(action, text, -1, parent, icon);
    }

    public JMenuItem addCommandItem(CommandMenuItem item, Parameters parameters) {
        if (item.getCommand() == null && item.getLabel() == null) {
            if (item.checkRestrictions(parameters)) {
                addSeparator(item.getPos(), item.getParent());
            }
            else {
                addItem("dummy"+item.getId(), "", item.getPos(), item.getParent(), null);
            }
        } else if (item.getCommand() == null) {
            JMenu menu = getSubmenu(item.getLabel(), item.getLabel(parameters), item.getPos());
            menu.setToolTipText(item.getTooltipHtml());
            addKey(item, menu);
            return menu;
        } else {
            commands.put(item.getId(), item.getCommand());
            JMenuItem mItem = addItem(item.getId(), item.getLabel(parameters), item.getPos(), item.getParent(), null);
            mItem.setToolTipText(item.getTooltipHtml());
            addKey(item, mItem);
            return mItem;
        }
        return null;
    }
    
    private void addKey(CommandMenuItem item, JMenuItem mItem) {
        if (item.hasKey()) {
            mItem.setMnemonic(KeyEvent.getExtendedKeyCodeForChar(item.getKey().toLowerCase().charAt(0)));
        }
    }
    
    private ActionEvent getCommandActionEvent(ActionEvent e) {
        CustomCommand command = commands.get(e.getActionCommand());
        if (command != null) {
            return new CommandActionEvent(e, command);
        }
        return e;
    }
    
    public void addCheckboxItem(String action, String text, boolean selected) {
        items.put(action, add(makeCheckboxItem(action, text, selected)));
    }
    
    public void addCheckboxItem(String action, String text, String parent, boolean selected) {
        if (parent != null) {
            JMenuItem item = makeCheckboxItem(action, text, selected);
            getSubmenu(parent).add(item);
            items.put(action, item);
        } else {
            addCheckboxItem(action, text, selected);
        }
    }
    
    public void addRadioItem(String action, String text, String group) {
        items.put(action, add(makeRadioItem(action, text, group)));
    }
    
    public void addRadioItem(String action, String text, String group, String parent) {
        if (parent != null) {
            JMenuItem item = makeRadioItem(action, text, group);
            getSubmenu(parent).add(item);
            items.put(action, item);
        } else {
            addRadioItem(action, text, group);
        }
    }
    
    /**
     * Adds a seperator to the submenu with the given name, or adds a seperator
     * in the main menu if this submenu doesn't exist yet.
     * 
     * @param parent 
     */
    protected void addSeparator(int pos, String parent) {
        if (parent != null && isSubmenu(parent)) {
            if (pos > -1) {
                getSubmenu(parent).insertSeparator(pos);
            } else {
                getSubmenu(parent).addSeparator();
            }
        } else {
            if (pos > -1) {
                insert(new Separator(), pos);
            } else {
                addSeparator();
            }
        }
    }
    
    public void addSeparator(String parent) {
        addSeparator(-1, parent);
    }
    
    public void addContextMenuListener(ContextMenuListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }
    
    protected Set<ContextMenuListener> getContextMenuListeners() {
        return listeners;
    }
    
    private boolean isSubmenu(String name) {
        return subMenus.containsKey(name);
    }
    
    public void registerSubmenu(JMenu menu) {
        subMenus.put(menu.getText(), menu);
    }
    
    private JMenu getSubmenu(String name, int pos) {
        return getSubmenu(name, name, pos);
    }
    
    private JMenu getSubmenu(String key, String name, int pos) {
        if (subMenus.get(key) == null) {
            JMenu menu = new JMenu(name);
            if (pos > -1) {
                insert(menu, pos);
            } else {
                add(menu);
            }
            subMenus.put(key, menu);
        }
        return subMenus.get(key);
    }
    
    private JMenu getSubmenu(String name) {
        return getSubmenu(name, -1);
    }
    
    protected void setSubMenuIcon(String name, ImageIcon icon) {
        getSubmenu(name, -1).setIcon(icon);
    }
    
    public JMenuItem getItem(String action) {
        return items.get(action);
    }
    
    public void removeEmpty() {
        for (JMenuItem item : items.values()) {
            if (item.getText().isEmpty()) {
                remove(item);
                for (JMenu menu : subMenus.values()) {
                    menu.remove(item);
                }
            }
        }
        for (JMenu menu : subMenus.values()) {
            if (menu.getText().isEmpty()) {
                remove(menu);
            }
        }
    }
 
}