
import { useState } from "react";
import api from "../api";
import "./Chatbot.css";

export default function Chatbot() {
  // State variables
  const [isOpen, setIsOpen] = useState(false);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  
  // Array of message objects
  const [messages, setMessages] = useState([
    { sender: "bot", text: "Hello! How can I help you today?" }
  ]);

  // Toggle chat window open and close
  const toggleChat = () => {
    setIsOpen(!isOpen);
  };

  // Handle input typing
  const handleChange = (e) => {
    setInput(e.target.value);
  };

  // Handle form submission
  const handleSend = async (e) => {
    e.preventDefault();

    // Check if input is empty
    if (input.trim() === "") {
      return;
    }

    const userText = input;
    
    // Create new message object for user
    const userMessage = { sender: "user", text: userText };
    
    // Add user message to existing messages list
    setMessages([...messages, userMessage]);
    
    // Clear the input field and show loading
    setInput("");
    setLoading(true);

    try {
      // Call backend API
      const response = await api.post("/genai/chat", { message: userText });
      
      // Create new message object for bot reply
      const botMessage = { sender: "bot", text: response.data.message };
      
      // Update state with bot reply
      setMessages((currentMessages) => [...currentMessages, botMessage]);
    } catch (error) {
      console.log("Error sending message:", error);
      
      // Show error message in chat
      const errorMessage = { sender: "bot", text: "Sorry, something went wrong." };
      setMessages((currentMessages) => [...currentMessages, errorMessage]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="chatbot-wrapper">
      {/* Toggle Button */}
      <button className="chat-toggle-btn" onClick={toggleChat}>
        {isOpen ? "Close Chat" : "AI Assistant"}
      </button>

      {/* Chat Window */}
      {isOpen && (
        <div className="chat-window">
          <div className="chat-header">
            <h4>Railway AI Assistant</h4>
          </div>

          <div className="chat-messages">
            {messages.map((item, index) => {
              return (
                <div key={index} className={"message-bubble " + item.sender}>
                  {item.text}
                </div>
              );
            })}
            
            {loading && <div className="message-bubble bot">Typing...</div>}
          </div>

          <form onSubmit={handleSend} className="chat-input-form">
            <input
              type="text"
              value={input}
              onChange={handleChange}
              placeholder="Ask a question..."
            />
            <button type="submit" disabled={loading}>
              Send
            </button>
          </form>
        </div>
      )}
    </div>
  );
}