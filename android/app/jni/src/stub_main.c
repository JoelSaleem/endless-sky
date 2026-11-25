#include "SDL.h"

/* Toolchain smoke test: fill the window with a solid colour for a few seconds.
   No GL headers needed — SDL_Renderer picks GLES under the hood. */
int main(int argc, char *argv[])
{
	(void)argc; (void)argv;
	if(SDL_Init(SDL_INIT_VIDEO) != 0)
	{
		SDL_Log("SDL_Init failed: %s", SDL_GetError());
		return 1;
	}
	SDL_Window *window = SDL_CreateWindow("endless-sky-stub", 0, 0, 0, 0, SDL_WINDOW_FULLSCREEN);
	if(!window)
	{
		SDL_Log("CreateWindow failed: %s", SDL_GetError());
		return 1;
	}
	SDL_Renderer *renderer = SDL_CreateRenderer(window, -1, SDL_RENDERER_ACCELERATED);
	if(!renderer)
	{
		SDL_Log("CreateRenderer failed: %s", SDL_GetError());
		return 1;
	}
	SDL_Log("endless-sky stub: window + renderer up");
	for(int i = 0; i < 300; ++i)
	{
		SDL_Event e;
		while(SDL_PollEvent(&e))
			if(e.type == SDL_QUIT)
				i = 300;
		SDL_SetRenderDrawColor(renderer, 20, 30, 80, 255);
		SDL_RenderClear(renderer);
		SDL_RenderPresent(renderer);
		SDL_Delay(16);
	}
	SDL_DestroyRenderer(renderer);
	SDL_DestroyWindow(window);
	SDL_Quit();
	return 0;
}
