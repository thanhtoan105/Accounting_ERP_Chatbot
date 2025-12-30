#!/bin/bash
SESSION="accounting-dev"

# Check if session exists
tmux has-session -t $SESSION 2>/dev/null

if [ $? != 0 ]; then
  echo "Creating new tmux session: $SESSION"

  # Create session with "servers" window
  tmux new-session -d -s $SESSION -n "servers"

  # Start Backend in top pane
  # Using 'cd backend' inside the pane to keep context clear
  tmux send-keys -t $SESSION:servers "cd backend && mvnd spring-boot:run -Dquickly" C-m

  # Split window horizontally (top/bottom) -v
  # Start Frontend in bottom pane
  tmux split-window -v -t $SESSION:servers
  tmux send-keys -t $SESSION:servers "cd frontend && pnpm dev" C-m

  # Create a separate "terminal" window for git/commands
  tmux new-window -t $SESSION -n "terminal"

  # Select the terminal window so you start there
  tmux select-window -t $SESSION:terminal
fi

echo "Session started. Attaching..."
tmux attach-session -t $SESSION
